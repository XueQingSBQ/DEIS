from __future__ import annotations

import json
import math
import re
import sys
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional

BASE_DIR = Path(__file__).resolve().parent
OUTPUT_DATASET = BASE_DIR / "open_catalog_dataset_360.json"
OUTPUT_SUMMARY = BASE_DIR / "open_catalog_dataset_360.summary.json"
OUTPUT_SOURCES = BASE_DIR / "source_manifest.json"

API_BASE = "https://catalog.data.gov/api/3/action/package_search"
FETCH_ROWS = 120
TARGET_PER_QUERY = 60
REGION_CODE = "1100"
DATA_CATEGORY = "public"
REGISTRATION_TYPE = "resource"
DEFAULT_NODE_ORDER = ["CNTA", "CNTB"]

NODE_CONFIG = {
    "RTAA": {
        "nodeId": "node1-country",
        "baseUrl": "http://127.0.0.1:8200",
        "organizationCode": "RTAA",
        "regionCode": REGION_CODE,
        "displayName": "country-root",
    },
    "PRAA": {
        "nodeId": "node2-province",
        "baseUrl": "http://127.0.0.1:8201",
        "organizationCode": "PRAA",
        "regionCode": REGION_CODE,
        "displayName": "province-a",
    },
    "CTAA": {
        "nodeId": "node3-city-a",
        "baseUrl": "http://127.0.0.1:8202",
        "organizationCode": "CTAA",
        "regionCode": REGION_CODE,
        "displayName": "city-a",
    },
    "CNTA": {
        "nodeId": "node4-county-a",
        "baseUrl": "http://127.0.0.1:8203",
        "organizationCode": "CNTA",
        "regionCode": REGION_CODE,
        "displayName": "county-a",
    },
    "CTAB": {
        "nodeId": "node5-city-b",
        "baseUrl": "http://127.0.0.1:8204",
        "organizationCode": "CTAB",
        "regionCode": REGION_CODE,
        "displayName": "city-b",
    },
    "CNTB": {
        "nodeId": "node6-county-b",
        "baseUrl": "http://127.0.0.1:8205",
        "organizationCode": "CNTB",
        "regionCode": REGION_CODE,
        "displayName": "county-b",
    },
}

QUERY_CONFIGS = [
    {
        "query": "transportation",
        "industry": "道路运输业",
        "applicationScenario": "cross-node transport data sharing",
        "ownerFallback": "transport-open-data",
    },
    {
        "query": "energy",
        "industry": "电力、热力生产和供应业",
        "applicationScenario": "energy data sharing and comparison",
        "ownerFallback": "energy-open-data",
    },
    {
        "query": "climate",
        "industry": "生态保护和环境治理业",
        "applicationScenario": "climate observation and monitoring",
        "ownerFallback": "climate-open-data",
    },
    {
        "query": "agriculture",
        "industry": "农业",
        "applicationScenario": "agriculture production and monitoring",
        "ownerFallback": "agriculture-open-data",
    },
    {
        "query": "health",
        "industry": "卫生",
        "applicationScenario": "public health data governance",
        "ownerFallback": "health-open-data",
    },
    {
        "query": "education",
        "industry": "教育",
        "applicationScenario": "education resource discovery",
        "ownerFallback": "education-open-data",
    },
]

TAG_RE = re.compile(r"<[^>]+>")
SPACE_RE = re.compile(r"\s+")


def fetch_json(url: str) -> Dict:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "dris-open-catalog-loader/1.0",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


def clean_text(value: Optional[str], fallback: str = "") -> str:
    text = value or fallback
    text = TAG_RE.sub(" ", text)
    text = SPACE_RE.sub(" ", text).strip()
    return text


def truncate(value: str, limit: int) -> str:
    value = value.strip()
    if len(value) <= limit:
        return value
    return value[: limit - 1].rstrip() + "…"


def normalize_format(value: Optional[str], url: str) -> str:
    raw = clean_text(value).lower()
    if raw:
        return raw[:32]
    parsed = urllib.parse.urlparse(url)
    suffix = Path(parsed.path).suffix.lower().lstrip(".")
    return suffix[:32] if suffix else "link"


def guess_access_method(fmt: str, url: str) -> str:
    fmt_l = fmt.lower()
    url_l = url.lower()
    api_formats = {
        "api",
        "json",
        "xml",
        "geojson",
        "arcgis geoservices rest api",
        "wms",
        "wfs",
    }
    download_formats = {
        "csv",
        "xls",
        "xlsx",
        "pdf",
        "zip",
        "doc",
        "html",
        "text",
        "text/tab",
        "zipped csv",
        "cfm",
        "link",
    }
    if fmt_l in api_formats:
        return "api"
    if fmt_l in download_formats:
        return "download"
    if any(marker in url_l for marker in ["format=json", "format=xml", "geojson", "/query?"]):
        return "api"
    return "download"


def guess_content_type(fmt: str, resource: Dict, url: str) -> str:
    mimetype = clean_text(resource.get("mimetype") or resource.get("mimetype_inner")).lower()
    if mimetype:
        return mimetype[:120]
    mapping = {
        "csv": "text/csv",
        "json": "application/json",
        "geojson": "application/geo+json",
        "xml": "application/xml",
        "pdf": "application/pdf",
        "xls": "application/vnd.ms-excel",
        "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "zip": "application/zip",
        "html": "text/html",
        "txt": "text/plain",
    }
    fmt_l = fmt.lower()
    if fmt_l in mapping:
        return mapping[fmt_l]
    suffix = Path(urllib.parse.urlparse(url).path).suffix.lower().lstrip(".")
    return mapping.get(suffix, "application/octet-stream")


def choose_resource(resources: List[Dict]) -> Optional[Dict]:
    if not resources:
        return None

    def score(item: Dict) -> tuple:
        url = clean_text(item.get("url"))
        fmt = normalize_format(item.get("format"), url)
        has_url = 0 if url else 1
        preferred = 0 if fmt in {"csv", "json", "geojson", "xml", "xlsx", "pdf"} else 1
        return (has_url, preferred, len(url))

    candidates = [item for item in resources if clean_text(item.get("url"))]
    if not candidates:
        return None
    candidates.sort(key=score)
    return candidates[0]


def source_dataset_url(dataset: Dict) -> str:
    name = clean_text(dataset.get("name"))
    if name:
        return f"https://catalog.data.gov/dataset/{urllib.parse.quote(name)}"
    identifier = clean_text(dataset.get("id"))
    if identifier:
        return f"https://catalog.data.gov/dataset/{urllib.parse.quote(identifier)}"
    return "https://catalog.data.gov/"


def build_record(dataset: Dict, query_cfg: Dict, node_key: str, serial_no: int) -> Dict:
    node = NODE_CONFIG[node_key]
    dataset_id = clean_text(dataset.get("id") or dataset.get("name"))
    title = truncate(clean_text(dataset.get("title"), dataset_id), 160)
    notes = truncate(clean_text(dataset.get("notes"), title), 480)
    tags = [clean_text(item.get("display_name") or item.get("name")) for item in dataset.get("tags", [])]
    tags = [item for item in tags if item]
    groups = [clean_text(item.get("display_name") or item.get("title") or item.get("name")) for item in dataset.get("groups", [])]
    groups = [item for item in groups if item]
    organization = clean_text((dataset.get("organization") or {}).get("title") or (dataset.get("organization") or {}).get("name"), query_cfg["ownerFallback"])
    owner = truncate(organization or query_cfg["ownerFallback"], 120)
    landing_page = source_dataset_url(dataset)
    resource = choose_resource(dataset.get("resources") or [])
    resource_url = clean_text(resource.get("url")) if resource else landing_page
    resource_format = normalize_format(resource.get("format") if resource else None, resource_url)
    access_method = "download"
    content_type = guess_content_type(resource_format, resource or {}, resource_url)
    entity_type = "dataset"
    resource_name = clean_text((resource or {}).get("name"), title)
    license_title = clean_text(dataset.get("license_title"), "CC0 / public metadata")
    created_at = clean_text((resource or {}).get("created") or dataset.get("metadata_created") or dataset.get("metadata_modified"))
    additional_attributes = {
        "_idempotencyKey": f"open-catalog-v2:{dataset_id}",
        "sourcePortal": "catalog.data.gov",
        "sourceQuery": query_cfg["query"],
        "sourceDatasetId": dataset_id,
        "sourceDatasetUrl": landing_page,
        "sourceResourceName": resource_name,
        "sourceLicense": license_title,
        "sourceOrganization": owner,
        "tagList": tags[:12],
        "groupList": groups[:8],
        "resourceCount": len(dataset.get("resources") or []),
        "assignedNodeId": node["nodeId"],
        "assignedNodeName": node["displayName"],
        "recordSerialNo": serial_no,
        "metadataCreated": clean_text(dataset.get("metadata_created")),
        "metadataModified": clean_text(dataset.get("metadata_modified")),
        "notesPreview": truncate(notes, 220),
    }

    registration_request = {
        "regionCode": node["regionCode"],
        "organizationCode": node["organizationCode"],
        "industry": query_cfg["industry"],
        "dataCategory": DATA_CATEGORY,
        "registrationType": REGISTRATION_TYPE,
        "owner": owner,
        "description": notes,
        "applicationScenario": query_cfg["applicationScenario"],
        "name": title,
        "entityType": entity_type,
        "additionalAttributes": additional_attributes,
    }

    resource_binding = {
        "componentType": "resource",
        "storageType": "external_uri",
        "accessMethod": access_method,
        "contentType": content_type,
        "format": resource_format,
        "locator": resource_url,
        "size": 0,
        "hashValue": "",
        "etag": clean_text((resource or {}).get("cache_last_updated")),
        "versionId": clean_text((resource or {}).get("revision_id") or dataset.get("revision_id")),
        "createdAt": created_at,
        "extra": {
            "sourceDatasetId": dataset_id,
            "sourceDatasetUrl": landing_page,
            "sourceResourceName": resource_name,
            "sourcePortal": "catalog.data.gov",
            "sourceQuery": query_cfg["query"],
            "sourceLicense": license_title,
            "resourcePosition": 1,
        },
    }

    return {
        "recordId": f"open-catalog-{serial_no:04d}",
        "source": {
            "query": query_cfg["query"],
            "datasetId": dataset_id,
            "datasetTitle": title,
            "datasetUrl": landing_page,
            "organization": owner,
            "licenseTitle": license_title,
            "metadataCreated": clean_text(dataset.get("metadata_created")),
            "metadataModified": clean_text(dataset.get("metadata_modified")),
            "resourceName": resource_name,
            "resourceFormat": resource_format,
            "resourceUrl": resource_url,
            "tagList": tags,
            "groupList": groups,
        },
        "targetNode": node,
        "registrationRequest": registration_request,
        "resourceBinding": resource_binding,
    }


def fetch_query_results(query: str) -> List[Dict]:
    params = {"rows": FETCH_ROWS, "q": query}
    url = API_BASE + "?" + urllib.parse.urlencode(params)
    payload = fetch_json(url)
    if not payload.get("success"):
        raise RuntimeError(f"query failed: {query}")
    return payload["result"]["results"]


def build_dataset() -> List[Dict]:
    dataset_records: List[Dict] = []
    seen_ids = set()
    serial_no = 1
    node_index = 0

    for query_cfg in QUERY_CONFIGS:
        results = fetch_query_results(query_cfg["query"])
        accepted = 0
        for dataset in results:
            dataset_id = clean_text(dataset.get("id") or dataset.get("name"))
            if not dataset_id or dataset_id in seen_ids:
                continue
            resource = choose_resource(dataset.get("resources") or [])
            if resource is None:
                continue
            node_key = DEFAULT_NODE_ORDER[node_index % len(DEFAULT_NODE_ORDER)]
            node_index += 1
            record = build_record(dataset, query_cfg, node_key, serial_no)
            dataset_records.append(record)
            seen_ids.add(dataset_id)
            serial_no += 1
            accepted += 1
            if accepted >= TARGET_PER_QUERY:
                break
        if accepted < TARGET_PER_QUERY:
            raise RuntimeError(f"query {query_cfg['query']} accepted {accepted}, expected {TARGET_PER_QUERY}")
    return dataset_records


def summarize(records: List[Dict]) -> Dict:
    by_node = Counter(record["targetNode"]["organizationCode"] for record in records)
    by_query = Counter(record["source"]["query"] for record in records)
    by_format = Counter(record["resourceBinding"]["format"] for record in records)
    by_entity_type = Counter(record["registrationRequest"]["entityType"] for record in records)

    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "recordCount": len(records),
        "nodeDistribution": dict(by_node),
        "queryDistribution": dict(by_query),
        "topFormats": dict(by_format.most_common(15)),
        "entityTypes": dict(by_entity_type),
        "sourcePortal": "catalog.data.gov",
        "sourceApiBase": API_BASE,
        "description": "Official open data metadata from Data.gov transformed into DRIS registration + external resource bindings.",
    }


def write_json(path: Path, payload: Dict | List) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    records = build_dataset()
    summary = summarize(records)
    sources = {
        "generatedAt": summary["generatedAt"],
        "sourcePortal": "catalog.data.gov",
        "sourceApiBase": API_BASE,
        "queries": QUERY_CONFIGS,
        "notes": [
            "Data.gov CKAN API returns metadata and resource URLs, not the dataset contents.",
            "This corpus keeps the external resource URL as DRIS component locator.",
            "Every record carries a stable _idempotencyKey based on the source dataset id.",
        ],
    }
    write_json(OUTPUT_DATASET, records)
    write_json(OUTPUT_SUMMARY, summary)
    write_json(OUTPUT_SOURCES, sources)
    print(f"generated {len(records)} records -> {OUTPUT_DATASET}")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


