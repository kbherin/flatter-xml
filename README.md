# Flatter XML
Collapse nested records in an XML into a collection of tabular files.

Useful for flattening a deeply nested XML data into delimited files to facilitate loading into tables.

Flattening starts from a primary `record-tag`, an element that identifies a top-level _record_.
The record element and all the elements nested under it are flattened into tabular files.
If it is not specified then the first tag that appears after the root tag is considered as the primary record tag.

## Minimum Requirements
Java: 1.8 and above.

## Dependencies
* Snake Yaml
* Commons CLI

## Usage

#### Library Use

##### Single Threaded
```java 
    FlattenXml flattener = new FlattenXml.FlattenXmlBuilder()
                .setXmlFilename("src/test/resources/emp.xml")   // Required
                .setOutDir("target/test/resources/emp_tables/") // Defaults to "."
                .setRecordTag("employee")                       // Inferred if not provided
                .setDelimiter("|")                              // Defaults to ","
                .setCascadePolicy("XSD")                        // Defaults to NONE
                .setRecordCascadeFieldsSeq(yamlFile1)           // Optionally define fields to cascade in a YAML
                .setRecordOutputFieldsSeq(yamlFile2)            // Optionally define output fields in a YAML
                .setXsdFiles("emp.xsd,contact.xsd".split(","))  // Optional, but preferrable
                .createFlattenXml();
    flattener.parseFlatten();
```
##### Concurrent Workers
```java 
    RecordHandler recordHandler = new DelimitedFileWriter(delimiter, outDir,
                    recordOutputFieldsDefFile != null || !xsds.isEmpty(),
                    statusReporter);
    XmlFlattenerWorkerFactory workerFactory = XmlFlattenerWorkerFactory.newInstance(
                    recordTag, xsds, cascadePolicy,
                    recordCascadeFieldsDefFile, recordOutputFieldsDefFile, /* yaml files */
                    batchSize, statusReporter);
    XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
    XmlRecordEmitter emitter = new XmlRecordEventEmitter.XmlEventEmitterBuilder()
                                       .setXmlFile(xmlFilePath)
                                       .create();
    workerPool.execute(numberOfWorkers, emitter, workerFactory);

```
Multiple workers version generates multiple partial files suffixed with _part1, _part2, etc, for each record type.
 
### Command Line
Use the main function in FlattenXmlRunner to run this on command line.
```shell script
usage: FlattenXmlRunner XMLFile [OPTIONS]
 -c,--cascades <arg>           Data for tags under a record(complex) type
                               element is cascaded to child records.
                               NONE|OUT|XSD|<record-fields-yaml>.
                               Defaults to NONE
 -d,--delimiter <arg>          Delimiter. Defaults to a comma(,)
 -l,--newline <arg>            Newline replacement character. Defaults to tilde(~)
 -f,--output-fields <arg>      Desired output fields for each record(complex)
                               type in a YAML file
 -n,--n-records <int>          Number of records to process in the XML
                               document
 -o,--output-dir <arg>         Output directory for generating tabular files.
                               Defaults to current directory
 -p,--progress <int>           Report progress after a batch. Defaults to 100
 -r,--record-tag <arg>         Primary record tag from where parsing begins.
                               If not provided entire file will be parsed
 -w,--workers <int>            Number of parallel workers. Defaults to 1
 -s,--stream-record-strings Y  Distribute XML records as strings to multiple workers.
                               Less safe but highly performant.
                               Defaults to streaming records as events
 -x,--xsd <arg>                XSD files. Comma separated list.
                               Format: emp_ns.xsd,phone_ns.xsd,...
```

#### Output Definition
For any record type(complex type) output fields and fields to cascade to child records,
can be specified with 
1) XSD
2) Record Definition File

##### Specifying the record definition file
User can define record definition files to define the output record and fields to cascade to child record.
1) Output fields for a record type can be specified with a call to `builder.setRecordOutputFieldsSeq(yamlFile2)`
2) Similarly, fields to cascade for a record type can be specified with `builder.setRecordCascadeFieldsSeq(yamlFile1)`

Both lists only support inclusion. 

##### Effect of record definition files, cascading options and XSDs on the output

|-f Output Def|-x XSD|-c Cascade Def| Tags on the Output Record              | _Result:_ Tags Cascaded to Child Record |
|-------------|------|--------------|----------------------------------------|-----------------------------------------|
|             |      |              |All simple tags in same seq as in XML   |No cascading to child record             |
|             |      |      OUT     |All simple tags in same seq as in XML   |Follows output tags of record            |
|             |x1.xsd|              |All simple tags in same seq as in XSD   |No cascading to child record             |
|             |x1.xsd|      XSD     |All simple tags in same seq as in XSD   |Follows tags seq in XSD file             |
|             |x1.xsd|      OUT     |All simple tags in same seq as in XSD   |Follows output tags of record            |
|   out.yml   |      |              |All tags in same seq defined in out.yml |No cascading to child record             |
|   out.yml   |      |      OUT     |All tags in same seq defined in out.yml |Follows output tags of record            |
|   out.yml   |      |   casc.yml   |All tags in same seq defined in out.yml |Follows tags seq defined in casc.yml     |
|   out.yml   |x1.xsd|   casc.yml   |All tags in same seq defined in out.yml |Follows tags seq defined in casc.yml     |
|   out.yml   |x1.xsd|              |All tags in same seq defined in out.yml |No cascading to child record             |
|   out.yml   |x1.xsd|      OUT     |All tags in same seq defined in out.yml |Follows output tags of record            |
|   out.yml   |x1.xsd|      XSD     |All tags in same seq defined in out.yml |Follows tags seq in XSD file             |

###### Column sequence handling summary
For columns in current record

1. `-x emp.xsd`          = record's columns follow the XSD file
2. `-f record_defs.yaml` = record's columns follow the user defined order. Overrides -x
3. `<-f unspecified>`    = record's columns follow the order of appearance in the XML file

For columns cascaded to descendant record

1. `-c XSD -x x1.xsd`    = columns cascaded to child records follow the tags in XSD file
2. `-c OUT`              = columns cascaded to child records follow the output record's fields
3. `-c NONE`             = no cascading to child records
4. `-c casc.yaml`        = columns cascaded to child records follow user defined order

## Examples

#### Example 1
An example of extracting information into CSV files about employees in an organization:
```xml
<employees xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
    xmlns='http://kbps.com/emp' xsi:schemaLocation='http://kbps.com/emp emp.xsd'>
  <employee> <!-- employee.csv: 00000001|Steve Rogers|public relations|150,000.00 -->
    <!-- first avenger -->
    <employee-no>00000001</employee-no>
    <employee-name>Steve Rogers</employee-name>
    <department>public relations</department>
    <salary>150,000.00</salary>
    <contact> <!-- contact.csv: empty -->
      <addresses> <!-- addresses.csv: empty -->
                   <!-- address.csv: Header fields indicate the source: address-type|line1|state|zip|employee.employee-no|employee.employee-name|employee.department|employee.salary--> 
        <address>  <!-- address.csv: With cascade policy "OUT" prints : primary|1 Tudor &amp; Place|NY, US|12345|00000001|Steve Rogers|public relations|150,000.00-->
          <address-type>primary</address-type>
          <line1>1 Tudor &amp; Place</line1>
          <state>NY, US</state>
          <zip>12345</zip>
          <reroute>  <!-- reroute.csv: While cascading, deepest info is presented first: <reroute-fields>|<address-fields>|<employee-fields> [deepest first]-->
            <employee-name>Nick Fury</employee-name>
            <line1>541E Summer St.</line1>
            <!-- While cascading with "XSD" policy "line2" field with empty value is printed, despite the element missing here. Always intend to use "XSD" policy -->
            <!-- But when cascading with "OUT" policy "line2" field will be missing in the output. "OUT" works only if XML file includes all the fields for complex types -->
            <state>NY, US</state>
            <zip>92478</zip>
          <!--  Header in reroute.csv: employee-name|line1|line2|state|zip|address.address-type|address.line1|address.state|address.zip|employee.employee-no|employee.employee-name|employee.department -->
          </reroute> <!-- reroute.csv: Nick Fury|541E Summer St.|NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|Steve Rogers|public relations|150,000.00 -->
        </address>
      </addresses>
      <phones>
        <phone>
          <phone-num>1234567890</phone-num>
          <phone-type>landline</phone-type>
        </phone>
      </phones>
    </contact>
  </employee>

```

If the top-level record tag is unavailable then it infers it as the tag that immediately follows the root tag.
In this case it is `<employee>`.

##### Record Definition File
An example of the record specification file used is in order.

The same file has been used for specifying output fields and cascading fields to child records.

```
# Namespace prefixes defined here can be used to define record fields
namespaces:
  "emp": "http://kbps.com/emp"
  "xsi": "http://www.w3.org/2001/XMLSchema-instance"
  "": "http://kbps.com/emp"              # Default namespace is specified with a "" and is required

records:
  "emp:employee":
    - "employee-no"                      # No NS URI or NS prefix
    - "{http://kbps.com/emp}department"  # With NS URI

  "{http://kbps.com/emp}address":        # Record name itsel should form a QName
    - "address-type"                     # Record's fields do not need a prefix or NS URI
    - "line1"
    - "emp:state"                        # With NS prefix
    - "zip"
```

##### Result
Given the above XML file the following flat files will be produced:
```
employee.csv
  |__contact.csv
    |__addresses.csv
      |__address.csv
        |__reroute.csv
    |__phones.csv
      |__phone.csv
```
## Authors
* Kartik Bherin