package com.dris.sdk.next;

import com.dris.sdk.next.adapter.memory.InMemoryDigitalObjectRepository;
import com.dris.sdk.next.adapter.memory.LocalTransactionRunner;
import com.dris.sdk.next.adapter.memory.SimpleRegistrationDictionary;
import com.dris.sdk.next.api.DrisSdk;
import com.dris.sdk.next.api.DrisSdkFactory;
import com.dris.sdk.next.api.IdentifierApplicationService;
import com.dris.sdk.next.api.MetadataApplicationService;
import com.dris.sdk.next.api.ResourceApplicationService;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierCode;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.enumtype.DataCategory;
import com.dris.sdk.next.enumtype.MetadataStatus;
import com.dris.sdk.next.enumtype.ObjectStatus;
import com.dris.sdk.next.enumtype.RegistrationType;
import com.dris.sdk.next.exception.ConflictException;
import com.dris.sdk.next.exception.DrisKernelErrorCode;
import com.dris.sdk.next.exception.NotFoundException;
import com.dris.sdk.next.infrastructure.IdentifierCredentialGenerator;
import com.dris.sdk.next.infrastructure.TimeProvider;
import com.dris.sdk.next.request.BindResourceRequest;
import com.dris.sdk.next.request.DeleteIdentifierRequest;
import com.dris.sdk.next.request.PatchMetadataRequest;
import com.dris.sdk.next.request.RegisterIdentifierRequest;
import com.dris.sdk.next.request.RemoveResourceRequest;
import com.dris.sdk.next.request.UpdateMetadataRequest;
import com.dris.sdk.next.result.BatchItemStatus;
import com.dris.sdk.next.result.BatchResult;
import com.dris.sdk.next.rule.IdentifierRules;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultKernelFlowTest {
    private static final String FIRST_IDENTIFIER = "1100ABCD013220260309000000000C";

    @Test
    void identifierRulesMatchLegacyCoreSpecification() {
        assertEquals("1", IdentifierRules.resolveDataCategoryCode(DataCategory.PUBLIC.getDisplayText()));
        assertEquals("2", IdentifierRules.resolveDataCategoryCode(DataCategory.ENTERPRISE.getDisplayText()));
        assertEquals("3", IdentifierRules.resolveDataCategoryCode(DataCategory.INDIVIDUAL.getDisplayText()));
        assertEquals("9", IdentifierRules.resolveDataCategoryCode(DataCategory.OTHERS.getDisplayText()));

        assertEquals("1", IdentifierRules.resolveRegistrationTypeCode(RegistrationType.RESOURCE.getDisplayText()));
        assertEquals("2", IdentifierRules.resolveRegistrationTypeCode(RegistrationType.ASSET.getDisplayText()));
        assertEquals("3", IdentifierRules.resolveRegistrationTypeCode(RegistrationType.PRODUCT_SERVICE.getDisplayText()));

        assertEquals('C', IdentifierRules.calculateCheckDigit("1100ABCD013220260309000000000"));
        assertEquals(FIRST_IDENTIFIER, IdentifierRules.composeIdentifier("1100ABCD013220260309", "000000000"));
    }

    @Test
    void registerCreatesDigitalObject() {
        Fixture fixture = new Fixture();

        String identifier = fixture.identifierService.register(baseRegisterRequest());

        assertEquals(FIRST_IDENTIFIER, identifier);
        assertTrue(fixture.identifierService.exists(identifier));
        assertTrue(fixture.identifierService.validate(identifier));
        assertFalse(fixture.identifierService.validate(identifier.substring(0, 29) + "1"));

        DigitalObject object = fixture.identifierService.getDigitalObject(identifier);
        assertEquals(identifier, object.getIdentifier());
        assertEquals(ObjectStatus.ACTIVE, object.getStatus());
        assertEquals(1L, object.getRevision());
        assertEquals("owner-a", object.getMetadata().getOwner());
        assertEquals("dataset-alpha", object.getMetadata().getName());
        assertEquals(DataCategory.INDIVIDUAL.getDisplayText(), object.getMetadata().getDataCategory());
        assertEquals(RegistrationType.ASSET.getDisplayText(), object.getMetadata().getRegistrationType());
        assertEquals("20260309", object.getMetadata().getDate());
        assertNotNull(object.getMetadata().getCreateTime());
        assertNotNull(object.getMetadata().getUpdateTime());
    }

    @Test
    void parseReturnsLegacyIdentifierStructure() {
        Fixture fixture = new Fixture();
        String identifier = fixture.identifierService.register(baseRegisterRequest());

        IdentifierCode code = fixture.identifierService.parse(identifier);
        assertEquals("1100", code.getRegionCode());
        assertEquals("ABCD", code.getOrganizationCode());
        assertEquals("01", code.getIndustryCode());
        assertEquals("3", code.getDataCategoryCode());
        assertEquals("2", code.getRegistrationTypeCode());
        assertEquals("20260309", code.getDate());
        assertEquals("000000000C", code.getCredential());
    }

    @Test
    void updateAndPatchMetadataWorkSeparately() {
        Fixture fixture = new Fixture();
        String identifier = fixture.identifierService.register(baseRegisterRequest());

        IdentifierMetadata replacement = new IdentifierMetadata();
        replacement.setIndustry("industry-demo");
        replacement.setDataCategory(DataCategory.INDIVIDUAL.getDisplayText());
        replacement.setRegistrationType(RegistrationType.ASSET.getDisplayText());
        replacement.setOwner("owner-b");
        replacement.setDescription("full-update");
        replacement.setApplicationScenario("analysis");
        replacement.setName("dataset-beta");
        replacement.setEntityType("file");
        replacement.setStatus(MetadataStatus.INACTIVE.wireValue());
        replacement.setAdditionalAttributes(Collections.<String, Object>singletonMap("source", "lab"));

        UpdateMetadataRequest update = new UpdateMetadataRequest();
        update.setIdentifier(identifier);
        update.setMetadata(replacement);
        IdentifierMetadata updated = fixture.metadataService.update(update);
        assertEquals("owner-b", updated.getOwner());
        assertEquals("dataset-beta", updated.getName());
        assertEquals(MetadataStatus.INACTIVE.wireValue(), updated.getStatus());
        assertEquals("1100", updated.getRegionCode());
        assertEquals("ABCD", updated.getOrganizationCode());
        assertEquals("20260309", updated.getDate());
        assertEquals("lab", updated.getAdditionalAttributes().get("source"));

        Map<String, Object> changes = new LinkedHashMap<String, Object>();
        changes.put("description", "patched-desc");
        changes.put("name", "dataset-gamma");
        changes.put("quality", "checked");

        PatchMetadataRequest patch = new PatchMetadataRequest();
        patch.setIdentifier(identifier);
        patch.setChanges(changes);
        IdentifierMetadata patched = fixture.metadataService.patch(patch);

        assertEquals("patched-desc", patched.getDescription());
        assertEquals("dataset-gamma", patched.getName());
        assertEquals("lab", patched.getAdditionalAttributes().get("source"));
        assertEquals("checked", patched.getAdditionalAttributes().get("quality"));
    }

    @Test
    void bindAndRemoveResourceWorkSeparately() {
        Fixture fixture = new Fixture();
        String identifier = fixture.identifierService.register(baseRegisterRequest());

        Resource resource = new Resource();
        resource.setComponentType("resource");
        resource.setStorageType("external_uri");
        resource.setAccessMethod("download");
        resource.setContentType("application/pdf");
        resource.setFormat("pdf");
        resource.setLocator("https://node-a/object.pdf");
        resource.setSize(1024L);
        resource.setHashValue("sha256:demo");

        BindResourceRequest bind = new BindResourceRequest();
        bind.setIdentifier(identifier);
        bind.setResource(resource);
        Resource bound = fixture.resourceService.bind(bind);

        assertEquals("resource", bound.getComponentType());
        assertEquals("external_uri", bound.getStorageType());
        assertNotNull(bound.getCreatedAt());
        assertEquals("https://node-a/object.pdf", fixture.resourceService.get(identifier).getLocator());

        RemoveResourceRequest remove = new RemoveResourceRequest();
        remove.setIdentifier(identifier);
        fixture.resourceService.remove(remove);
        assertThrows(NotFoundException.class, () -> fixture.resourceService.get(identifier));
    }

    @Test
    void deleteAndBatchLookupWork() {
        Fixture fixture = new Fixture();
        String deletedIdentifier = fixture.identifierService.register(baseRegisterRequest());
        String activeIdentifier = fixture.identifierService.register(baseRegisterRequest());

        DeleteIdentifierRequest delete = new DeleteIdentifierRequest();
        delete.setIdentifier(deletedIdentifier);
        fixture.identifierService.delete(delete);

        assertFalse(fixture.identifierService.exists(deletedIdentifier));
        assertThrows(NotFoundException.class, () -> fixture.identifierService.getDigitalObject(deletedIdentifier));

        BatchResult<DigitalObject> batch = fixture.identifierService.getDigitalObjects(Arrays.asList(activeIdentifier, deletedIdentifier));
        assertEquals(2, batch.getTotalCount());
        assertEquals(1, batch.getSuccessCount());
        assertEquals(1, batch.getFailureCount());
        assertEquals(BatchItemStatus.SUCCESS, batch.getItems().get(0).getStatus());
        assertEquals(BatchItemStatus.NOT_FOUND, batch.getItems().get(1).getStatus());
        assertEquals(DrisKernelErrorCode.NOT_FOUND.name(), batch.getItems().get(1).getCode());
        assertNull(batch.getItems().get(1).getData());
    }

    @Test
    void batchRegisterAndFacadeWork() {
        Fixture fixture = new Fixture();
        BatchResult<String> batch = fixture.sdk.identifiers().registerBatch(Arrays.asList(
                baseRegisterRequest(),
                invalidRegisterRequest()
        ));

        assertEquals(2, batch.getTotalCount());
        assertEquals(1, batch.getSuccessCount());
        assertEquals(1, batch.getFailureCount());
        assertEquals("OK", batch.getItems().get(0).getCode());
        assertEquals(DrisKernelErrorCode.VALIDATION_ERROR.name(), batch.getItems().get(1).getCode());
        assertEquals(BatchItemStatus.FAILED, batch.getItems().get(1).getStatus());
    }

    @Test
    void idempotencyAndExpectedRevisionWork() {
        Fixture fixture = new Fixture();
        RegisterIdentifierRequest request = baseRegisterRequest();
        request.setIdempotencyKey("k1");
        String first = fixture.identifierService.register(request);
        String second = fixture.identifierService.register(request);
        assertEquals(first, second);

        UpdateMetadataRequest update = new UpdateMetadataRequest();
        update.setIdentifier(first);
        update.setExpectedRevision(999L);
        IdentifierMetadata metadata = fixture.metadataService.get(first);
        metadata.setDescription("x");
        update.setMetadata(metadata);
        ConflictException conflict = assertThrows(ConflictException.class, () -> fixture.metadataService.update(update));
        assertEquals(DrisKernelErrorCode.CONFLICT, conflict.getErrorCode());

        UpdateMetadataRequest ok = new UpdateMetadataRequest();
        ok.setIdentifier(first);
        ok.setExpectedRevision(1L);
        IdentifierMetadata m2 = fixture.metadataService.get(first);
        m2.setDescription("ok");
        ok.setMetadata(m2);
        fixture.metadataService.update(ok);
        assertEquals("ok", fixture.metadataService.get(first).getDescription());
    }

    private RegisterIdentifierRequest baseRegisterRequest() {
        RegisterIdentifierRequest request = new RegisterIdentifierRequest();
        request.setRegionCode("1100");
        request.setOrganizationCode("ABCD");
        request.setIndustry("industry-demo");
        request.setDataCategory(DataCategory.INDIVIDUAL.getDisplayText());
        request.setRegistrationType(RegistrationType.ASSET.getDisplayText());
        request.setOwner("owner-a");
        request.setDescription("demo-data");
        request.setApplicationScenario("sharing");
        request.setName("dataset-alpha");
        request.setEntityType("file");
        request.setAdditionalAttributes(Collections.<String, Object>singletonMap("tenant", "node-a"));
        return request;
    }

    private RegisterIdentifierRequest invalidRegisterRequest() {
        RegisterIdentifierRequest request = new RegisterIdentifierRequest();
        request.setRegionCode("1100");
        request.setOrganizationCode("ABCD");
        request.setDataCategory(DataCategory.INDIVIDUAL.getDisplayText());
        request.setRegistrationType(RegistrationType.ASSET.getDisplayText());
        request.setOwner("owner-a");
        return request;
    }

    private static class Fixture {
        private final DrisSdk sdk;
        private final IdentifierApplicationService identifierService;
        private final MetadataApplicationService metadataService;
        private final ResourceApplicationService resourceService;

        private Fixture() {
            InMemoryDigitalObjectRepository repository = new InMemoryDigitalObjectRepository();
            SimpleRegistrationDictionary dictionary = new SimpleRegistrationDictionary()
                    .addRegion("1100")
                    .addOrganization("ABCD")
                    .addIndustry("industry-demo", "01");
            LocalTransactionRunner transactionRunner = new LocalTransactionRunner();
            TimeProvider timeProvider = new FixedTimeProvider();
            IdentifierCredentialGenerator credentialGenerator = new FixedCredentialGenerator();

            this.sdk = DrisSdkFactory.compose(
                    repository,
                    dictionary,
                    transactionRunner,
                    timeProvider,
                    credentialGenerator
            );
            this.identifierService = sdk.identifiers();
            this.metadataService = sdk.metadata();
            this.resourceService = sdk.resources();
        }
    }

    private static class FixedTimeProvider implements TimeProvider {
        @Override
        public Instant now() {
            return Instant.parse("2026-03-09T00:00:00Z");
        }
    }

    private static class FixedCredentialGenerator implements IdentifierCredentialGenerator {
        private long sequence;

        @Override
        public String nextCredential(int length) {
            String seed = String.valueOf(sequence++);
            while (seed.length() < length) {
                seed = "0" + seed;
            }
            if (seed.length() > length) {
                seed = seed.substring(seed.length() - length);
            }
            return seed;
        }
    }
}
