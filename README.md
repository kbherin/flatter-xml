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
##### Single threaded use
```java 
    FlattenXml flattener = new FlattenXml.FlattenXmlBuilder()
                .setXmlFilename("src/test/resources/emp.xml")   // Required
                .setOutDir("target/test/resources/emp_tables/") // Defaults to "."
                .setRecordTag("employee")                       // Inferred if not provided
                .setDelimiter("|")                              // Defaults to ","
                .setCascadePolicy("XSD")                        // Defaults to NONE
                .setXsdFiles("emp.xsd,contact.xsd".split(","))  // Optional, but preferrable
                .createFlattenXml();
    flattener.parseFlatten();
```
##### Concurrent workers
```java 
    XmlFlattenerWorkerFactory workerFactory = XmlFlattenerWorkerFactory.newInstance(
                    xmlFilePath, outDir, delimiter, /* Required */
                    recordTag, xsds, cascadePolicy, recordCascadesTemplates,
                    batchSize, statusReporter);     /* Required */
    XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
    workerPool.execute(numWorkers, emitter, workerFactory);

```
Multiple worker version generates multiple partial files suffixed with _part1, _part2, etc, for each record
 
### Command Line
Use the main function in FlattenXmlRunner to run this on command line.
```shell script
usage: FlattenXmlRunner XMLFile [OPTIONS]
 -c,--cascades <arg>     Data of specified tags on parent element is
                         cascaded to child elements.
                         NONE|ALL|XSD. Defaults to NONE.
                         Format: elem1:tag1,tag2;elem2:tag1,tag2;...
 -d,--delimiter <arg>    Delimiter. Defaults to a comma(,)
 -n,--n-records <arg>    Number of records to process in the XML document
 -o,--output-dir <arg>   Output directory for generating tabular files.
                         Defaults to current directory
 -p,--progress <arg>     Report progress after a batch. Defaults to 100
 -r,--record-tag <arg>   Primary record tag from where parsing begins. If
                         not provided entire file will be parsed
 -w,--workers <arg>      Number of parallel workers. Defaults to 1
 -x,--xsd <arg>          XSD files. Comma separated list.
                         Format: emp.xsd,contact.xsd,...
```

    
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

If "record identifying" tag is not provided, it infers it as the tag that immediately follows the root tag.
In this case it is `<employee>`.

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