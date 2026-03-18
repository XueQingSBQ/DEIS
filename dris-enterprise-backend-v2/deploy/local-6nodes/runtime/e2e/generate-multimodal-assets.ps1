Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$assetDir = Join-Path $root 'assets'
New-Item -ItemType Directory -Force -Path $assetDir | Out-Null

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Reset-Dir {
    param([string]$Path)
    if (Test-Path $Path) {
        Remove-Item -Recurse -Force $Path
    }
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

$pngPath = Join-Path $assetDir 'sample-image.png'
$bmp = New-Object System.Drawing.Bitmap 160, 100
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(240,248,255))
$brush1 = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(33,150,243))
$brush2 = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,193,7))
$font = New-Object System.Drawing.Font('Arial', 18, [System.Drawing.FontStyle]::Bold)
$g.FillRectangle($brush1, 8, 8, 144, 48)
$g.FillEllipse($brush2, 112, 52, 36, 36)
$g.DrawString('DRIS IMG', $font, [System.Drawing.Brushes]::White, 18, 18)
$bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $brush1.Dispose(); $brush2.Dispose(); $font.Dispose()

$xlsxTemp = Join-Path $assetDir 'tmp-xlsx'
Reset-Dir $xlsxTemp
New-Item -ItemType Directory -Force -Path (Join-Path $xlsxTemp '_rels') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $xlsxTemp 'xl\_rels') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $xlsxTemp 'xl\worksheets') | Out-Null
Write-Utf8NoBom (Join-Path $xlsxTemp '[Content_Types].xml') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>
"@
Write-Utf8NoBom (Join-Path $xlsxTemp '_rels\.rels') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
"@
Write-Utf8NoBom (Join-Path $xlsxTemp 'xl\workbook.xml') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>
"@
Write-Utf8NoBom (Join-Path $xlsxTemp 'xl\_rels\workbook.xml.rels') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
"@
Write-Utf8NoBom (Join-Path $xlsxTemp 'xl\worksheets\sheet1.xml') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1">
      <c r="A1" t="inlineStr"><is><t>Region</t></is></c>
      <c r="B1" t="inlineStr"><is><t>Value</t></is></c>
    </row>
    <row r="2">
      <c r="A2" t="inlineStr"><is><t>CNTA</t></is></c>
      <c r="B2"><v>128</v></c>
    </row>
    <row r="3">
      <c r="A3" t="inlineStr"><is><t>CNTB</t></is></c>
      <c r="B3"><v>256</v></c>
    </row>
  </sheetData>
</worksheet>
"@
Write-Utf8NoBom (Join-Path $xlsxTemp 'xl\styles.xml') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf/></cellStyleXfs>
  <cellXfs count="1"><xf xfId="0"/></cellXfs>
</styleSheet>
"@
$xlsxPath = Join-Path $assetDir 'sample-table.xlsx'
if (Test-Path $xlsxPath) { Remove-Item $xlsxPath -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($xlsxTemp, $xlsxPath)

$docxTemp = Join-Path $assetDir 'tmp-docx'
Reset-Dir $docxTemp
New-Item -ItemType Directory -Force -Path (Join-Path $docxTemp '_rels') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $docxTemp 'word') | Out-Null
Write-Utf8NoBom (Join-Path $docxTemp '[Content_Types].xml') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
"@
Write-Utf8NoBom (Join-Path $docxTemp '_rels\.rels') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
"@
Write-Utf8NoBom (Join-Path $docxTemp 'word\document.xml') @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:t>DRIS multi modal document sample.</w:t></w:r></w:p>
    <w:p><w:r><w:t>Generated for COS upload verification.</w:t></w:r></w:p>
    <w:sectPr/>
  </w:body>
</w:document>
"@
$docxPath = Join-Path $assetDir 'sample-doc.docx'
if (Test-Path $docxPath) { Remove-Item $docxPath -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($docxTemp, $docxPath)

[ordered]@{
  png=$pngPath
  xlsx=$xlsxPath
  docx=$docxPath
} | ConvertTo-Json -Depth 4
