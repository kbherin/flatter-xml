# Flatter XML
Collapse elements in XML into a collection of tabular files.
Useful for flattening a deeply nested XML elements into delimited files or load into tables.

Flattening starts from `main-record-tag` and all elements nested under it.
If it is not specified then the first tag that appears after the root tag is considered as the primary record tag.

## Minimum Requirements
Java: 1.7 and above.

## Usage

### Library Use
    FlattenXml flattener = new FlattenXml.FlattenXmlBuilder()
                .setXmlFilename("./book-catalog.xml")
                .setOutDir("./book_catalog_tables/")
                .setRecordTag("book")
                .setDelimiter(",")
                .createFlattenXml();
    
    flattener.parseFlatten();
    
### Command Line
Use the main function in FlattenXmlRunner to run this on command line.

    FlattenXmlRunner <xml-file-path> [<output-dir> <main-record-tag> <num-recs> <delimiter> <batch-size>] 
    
      main-record-tag: Everything within it is flattened into individual tabular files.
    
      num-recs: if provided, processing stops after first N records. Defaults to 0, implying full file.
    
      batch-size: Progress is reported at the end of each batch. Defaults to 100.

## Examples
_TODO_

## Authors
1. Kartik Bherin