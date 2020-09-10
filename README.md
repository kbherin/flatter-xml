# Flatter XML
Collapse nested records in an XML into a collection of tabular files.

Useful for flattening a deeply nested XML data into delimited files to facilitate loading into tables.

Flattening starts from a primary `record-tag`, an element that identifies a _record_.
The record element and all the elements nested under it are flattened.
If it is not specified then the first tag that appears after the root tag is considered as the primary record tag.

## Minimum Requirements
Java: 1.8 and above.

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
    XmlFlattenerWorkerFactory workerFactory = XmlFlattenerWorkerFactory.newInstance(
                    xmlFilePath, outDir, delimiter, /* Required */
                    recordTag, xsds, cascadePolicy, recordCascadeFieldsDefFile, recordOutputFieldsDefFile,
                    batchSize, statusReporter);     /* Required */
    XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
    workerPool.execute(numberOfWorkers, emitter, workerFactory);

```
Multiple workers version generates multiple partial files suffixed with _part1, _part2, etc, for each record type.
 
### Command Line
Use the main function in FlattenXmlRunner to run this on command line.
```shell script
usage: FlattenXmlRunner XMLFile [OPTIONS]
 -c,--cascades <arg>        Data for tags under a record(complex) type
                            element is cascaded to child records.
                            NONE|ALL|XSD|<record-fields-yaml>.
                            Defaults to NONE
 -d,--delimiter <arg>       Delimiter. Defaults to a comma(,)
 -f,--output-fields <arg>   Desired output fields for each record(complex)
                            type in a YAML file
 -n,--n-records <arg>       Number of records to process in the XML
                            document
 -o,--output-dir <arg>      Output directory for generating tabular files.
                            Defaults to current directory
 -p,--progress <arg>        Report progress after a batch. Defaults to 100
 -r,--record-tag <arg>      Primary record tag from where parsing begins.
                            If not provided entire file will be parsed
 -w,--workers <arg>         Number of parallel workers. Defaults to 1
 -x,--xsd <arg>             XSD files. Comma separated list.
                            Format: emp_ns.xsd,phone_ns.xsd,...
```

#### Output Definition
For any record type(complex type) output fields and fields to cascade to child records,
can be specified with 
1) XSD
2) Record Specification File

##### Specifying the record definition file
User can define either the output fields or fields to cascade from parent to child or both.
1) Output fields for a record type can be specified with a call to `builder.setRecordOutputFieldsSeq(yamlFile2)`
2) Similarly, fields to cascade for a record type can be specified with `builder.setRecordCascadeFieldsSeq(yamlFile1)`

Both lists only support inclusion. 

##### Effect of cascading fields, output fields and XSDs on the output

|-f Output Def|-c Cascade Def|-x XSD| _Result:_ Output Fields                | _Result:_ Cascaded Fields              |
|-------------|--------------|------|----------------------------------------|----------------------------------------|
|    No       |       No     |  No  |All simple tags as they appear in XML   |No cascading from parent record         |
|    No       |      "ALL"   |  No  |All simple tags as they appear in XML   |All tags cascade to child record        |
|    No       |       No     | Yes  |All simple tags in same seq as in XSD   |All XSD mandatory tags cascade to child |
|    No       |      "ALL"   | Yes  |All simple tags in same seq as in XSD   |All tags cascade to child record        |
|    Yes      |       No     | Yes  |Simple tags & their seq as in Output Def|All XSD mandatory tags cascade to child |
|    No       |       Yes    | Yes  |All simple tags, but in XSD seq         |Specified tags & seq as in Cascade Def  |
|    Yes      |       No     |  No  |Simple tags & their seq as in Output Def|No cascading from parent record         |
|    No       |       Yes    |  No  |All simple tags as their appear in XML  |Specified tags & seq as in Cascade Def  |
|    Yes      |       Yes    |  No  |Simple tags & their seq as in Output Def|Specified tags & seq as in Cascade Def  |
|    Yes      |       Yes    | Yes  |Simple tags & their seq as in Output Def|Specified tags & seq as in Cascade Def  |

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
        <address>  <!-- address.csv: With cascade policy "ALL" prints : primary|1 Tudor &amp; Place|NY, US|12345|00000001|Steve Rogers|public relations|150,000.00-->
          <address-type>primary</address-type>
          <line1>1 Tudor &amp; Place</line1>
          <state>NY, US</state>
          <zip>12345</zip>
          <reroute>  <!-- reroute.csv: While cascading, deepest info is presented first: <reroute-fields>|<address-fields>|<employee-fields> [deepest first]-->
            <employee-name>Nick Fury</employee-name>
            <line1>541E Summer St.</line1>
            <!-- While cascading with "XSD" policy "line2" field with empty value is printed, despite the element missing here. Always intend to use "XSD" policy -->
            <!-- But when cascading with "ALL" policy "line2" field will be missing in the output. "ALL" works only if XML file includes all the fields for complex types -->
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

If "record identifying" tag is unavailable then it infers it as the tag that immediately follows the root tag.
In this case it is `<employee>`.

##### Record Definition File
An example of the record specification file used is in order.

The same file has been used for specifying output fields and cascading fields to child records.

```
# Namespace prefixes defined here can be used to define record fields
namespaces:
  "emp": "http://kbps.com/emp"
  "xsi": "http://www.w3.org/2001/XMLSchema-instance"

records:
  "emp:employee":
    - "employee-no"                      # No NS URI or NS prefix
    - "{http://kbps.com/emp}department"  # With NS URI

  "{http://kbps.com/emp}address":        # Record name itsel should form a QName
    - "address-type"                     # Record's fields do not need a prefix or NS URI
    - "line1"
    - "emp:state"                        # With NS prefixAML
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