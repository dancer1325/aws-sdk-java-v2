## Overview

* == Mid-level DynamoDB mapper/abstraction -- for -- AWS SDK Java v2

## Getting Started

* ALL the examples below use a fictional "Customer" class

### Initialization
1. ".java" / map records <- to & from -> database table

   ```java
   @DynamoDbBean
   public class Customer {
       private String accountId;
       private int subId;            // primitive types are supported
       private String name;
       private Instant createdDate;
       
       @DynamoDbPartitionKey
       public String getAccountId() { return this.accountId; }
       public void setAccountId(String accountId) { this.accountId = accountId; }
       
       @DynamoDbSortKey
       public int getSubId() { return this.subId; }
       public void setSubId(int subId) { this.subId = subId; }
       
       // Defines a GSI (customers_by_name) with a partition key of 'name'
       @DynamoDbSecondaryPartitionKey(indexNames = "customers_by_name")
       public String getName() { return this.name; }
       public void setName(String name) { this.name = name; }
       
       // Defines an LSI (customers_by_date) with a sort key of 'createdDate' and also declares the 
       // same attribute as a sort key for the GSI named 'customers_by_name'
       @DynamoDbSecondarySortKey(indexNames = {"customers_by_date", "customers_by_name"})
       public Instant getCreatedDate() { return this.createdDate; }
       public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }
   }
   ```
   
2. ways to create a `TableSchema` for your class / 
   1. -- via -- static constructor
      1. scan your annotated class
      2. infer the table structure & attributes
      
         ```java
         // 
         static final TableSchema<Customer> CUSTOMER_TABLE_SCHEMA = TableSchema.fromClass(Customer.class);
         ```
   2. declare directly the schema
      1. 👁️-> class does NOT need to be annotated 👁️
            
         ```java
            static final TableSchema<Customer> CUSTOMER_TABLE_SCHEMA =
              TableSchema.builder(Customer.class)
                .newItemSupplier(Customer::new)
                .addAttribute(String.class, a -> a.name("account_id")
                                                  .getter(Customer::getAccountId)
                                                  .setter(Customer::setAccountId)
                                                  .tags(primaryPartitionKey()))
                .addAttribute(Integer.class, a -> a.name("sub_id")
                                                   .getter(Customer::getSubId)
                                                   .setter(Customer::setSubId)
                                                   .tags(primarySortKey()))
                .addAttribute(String.class, a -> a.name("name")
                                                  .getter(Customer::getName)
                                                  .setter(Customer::setName)
                                                  .tags(secondaryPartitionKey("customers_by_name")))
                .addAttribute(Instant.class, a -> a.name("created_date")
                                                   .getter(Customer::getCreatedDate)
                                                   .setter(Customer::setCreatedDate)
                                                   .tags(secondarySortKey("customers_by_date"),
                                                         secondarySortKey("customers_by_name")))
                .build();
         ```
   
3. Create a `DynamoDbEnhancedClient`
   1. allows
      1. executing operations -- against -- ALL your tables 
      
         ```java
         DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                                                                       .dynamoDbClient(dynamoDbClient)
                                                                       .build();
         ```
4. Create a `DynamoDbTable`
   1. allows
      1. executing operations -- against a -- SPECIFIC table
      2. if the specific table does NOT ALREADY exist -> can be used | subsequent  `createTable()`
      
         ```java
         // Maps a physical table with the name 'customers_20190205' to the schema 
         DynamoDbTable<Customer> customerTable = enhancedClient.table("customers_20190205", CUSTOMER_TABLE_SCHEMA);
         ```

 
### Common primitive operations

* THESE ALL -- strongly map to the -- primitive DynamoDB operations
  * each operation -- can be customized, by passing in an -- enhanced request object
    * enhanced request objects -- offer most of the -- features / available | low-level DynamoDB SDK client

```java
   // CreateTable
   customerTable.createTable();
   
   // GetItem
   Customer customer = customerTable.getItem(Key.builder().partitionValue("a123").build());

   // UpdateItem
   Customer updatedCustomer = customerTable.updateItem(customer);
   
   // PutItem
   customerTable.putItem(customer);
   
   // DeleteItem
   Customer deletedCustomer = customerTable.deleteItem(Key.builder().partitionValue("a123").sortValue(456).build());
   
   // Query
   PageIterable<Customer> customers = customerTable.query(keyEqualTo(k -> k.partitionValue("a123")));

   // Scan
   PageIterable<Customer> customers = customerTable.scan();
   
   // BatchGetItem
   BatchGetResultPageIterable batchResults = enhancedClient.batchGetItem(r -> r.addReadBatch(ReadBatch.builder(Customer.class)
                                                                               .mappedTableResource(customerTable)
                                                                               .addGetItem(key1)
                                                                               .addGetItem(key2)
                                                                               .addGetItem(key3)
                                                                               .build()));
   
   // BatchWriteItem
   batchResults = enhancedClient.batchWriteItem(r -> r.addWriteBatch(WriteBatch.builder(Customer.class)
                                                                               .mappedTableResource(customerTable)
                                                                               .addPutItem(customer)
                                                                               .addDeleteItem(key1)
                                                                               .addDeleteItem(key1)
                                                                               .build()));
   
   // TransactGetItems
   transactResults = enhancedClient.transactGetItems(r -> r.addGetItem(customerTable, key1)
                                                           .addGetItem(customerTable, key2));
   
   // TransactWriteItems
   enhancedClient.transactWriteItems(r -> r.addConditionCheck(customerTable, 
                                                              i -> i.key(orderKey)
                                                                    .conditionExpression(conditionExpression))
                                           .addUpdateItem(customerTable, customer)
                                           .addDeleteItem(customerTable, key));
```
 
### Using secondary indices

* uses
  * for certain operations (Query and Scan)

```java
     DynamoDbIndex<Customer> customersByName = customerTable.index("customers_by_name");
       
     SdkIterable<Page<Customer>> customersWithName = 
         customersByName.query(r -> r.queryConditional(keyEqualTo(k -> k.partitionValue("Smith"))));
   
     PageIterable<Customer> pages = PageIterable.create(customersWithName);
```

### Working with immutable data classes

* DynamoDB Enhanced Client <- can map directly to and from -> immutable data classes | Java
  * requirements of the immutable class  
    * should ONLY
      * have -- getters
      * be associated with -- separate builder class /
        * -- used to construct -- instances of the immutable data class
    * ALL method | immutable class / is NOT an override of `Object.class` or annotated with `@DynamoDbIgnore` -- must be a -- getter for an attribute  
    * ALL getter | immutable class -- must have a -- corresponding setter | builder class / has a case-sensitive matching name
    * builder class -- must have a -- public default constructor OR public static method named 'builder' / takes NO parameters and returns an instance of the builder class | immutable class
    * builder class -- must have a -- public method named 'build' / takes NO parameters and returns an instance of the immutable class
  * `@DynamoDbImmutableThe` vs `@DynamoDbBean`
    * == very similar


```java
@DynamoDbImmutable(builder = Customer.Builder.class)
public class Customer {
    private final String accountId;
    private final int subId;        
    private final String name;
    private final Instant createdDate;
    
    private Customer(Builder b) {
        this.accountId = b.accountId;
        this.subId = b.subId;
        this.name = b.name;
        this.createdDate = b.createdDate;
    }   

    // This method will be automatically discovered and used by the TableSchema
    public static Builder builder() { return new Builder(); }

    @DynamoDbPartitionKey
    public String accountId() { return this.accountId; }
    
    @DynamoDbSortKey
    public int subId() { return this.subId; }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "customers_by_name")
    public String name() { return this.name; }
    
    @DynamoDbSecondarySortKey(indexNames = {"customers_by_date", "customers_by_name"})
    public Instant createdDate() { return this.createdDate; }
    
    public static final class Builder {
        private String accountId;
        private int subId;        
        private String name;
        private Instant createdDate;

        private Builder() {}

        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder subId(int subId) { this.subId = subId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder createdDate(Instant createdDate) { this.createdDate = createdDate; return this; }

        // This method will be automatically discovered and used by the TableSchema
        public Customer build() { return new Customer(this); }
    }
}
```

* `TableSchema`
  * if you want to create it / your immutable class -> use the `TableSchema.fromImmutableClass()`

   ```java
   // static constructor
   static final TableSchema<Customer> CUSTOMER_TABLE_SCHEMA = TableSchema.fromImmutableClass(Customer.class);
   ```
   
* some TP library
  * allows
    * generating a lot of the boilerplate code / -- associated with -- immutable objects
  * requirements
    * follow the previous conventions
  * _Example:_ Lombok

   ```java
       @Value
       @Builder
       @DynamoDbImmutable(builder = Customer.CustomerBuilder.class)
       public static class Customer {
           // 'onMethod'  copy the attribute DynamoDb annotations | generated code
           @Getter(onMethod = @__({@DynamoDbPartitionKey}))
           private String accountId;
   
           @Getter(onMethod = @__({@DynamoDbSortKey}))
           private int subId;  
         
           @Getter(onMethod = @__({@DynamoDbSecondaryPartitionKey(indexNames = "customers_by_name")}))
           private String name;
   
           @Getter(onMethod = @__({@DynamoDbSecondarySortKey(indexNames = {"customers_by_date", "customers_by_name"})}))
           private Instant createdDate;
       }
   ```

### Non-blocking asynchronous operations

* TODO:
If your application requires non-blocking asynchronous calls to
DynamoDb, then you can use the asynchronous implementation of the
mapper. It's very similar to the synchronous implementation with a few
key differences:

1. When instantiating the mapped database, use the asynchronous version
   of the library instead of the synchronous one (you will need to use
   an asynchronous DynamoDb client from the SDK as well):
   ```java
    DynamoDbEnhancedAsyncClient enhancedClient = 
        DynamoDbEnhancedAsyncClient.builder()
                                   .dynamoDbClient(dynamoDbAsyncClient)
                                   .build();
   ```

2. Operations that return a single data item will return a
   CompletableFuture of the result instead of just the result. Your
   application can then do other work without having to block on the
   result:
   ```java
   CompletableFuture<Customer> result = mappedTable.getItem(r -> r.key(customerKey));
   // Perform other work here
   return result.join();   // now block and wait for the result
   ```

3. Operations that return paginated lists of results will return an
   SdkPublisher of the results instead of an SdkIterable. Your
   application can then subscribe a handler to that publisher and deal
   with the results asynchronously without having to block:
   ```java
   PagePublisher<Customer> results = mappedTable.query(r -> r.queryConditional(keyEqualTo(k -> k.partitionValue("Smith"))));
   results.subscribe(myCustomerResultsProcessor);
   // Perform other work and let the processor handle the results asynchronously
   ```

## Using extensions
The mapper supports plugin extensions to provide enhanced functionality
beyond the simple primitive mapped operations. Extensions have two hooks, beforeWrite() and
afterRead(); the former can modify a write operation before it happens,
and the latter can modify the results of a read operation after it
happens. Some operations such as UpdateItem perform both a write and
then a read, so call both hooks.

Extensions are loaded in the order they are specified in the enhanced client builder. This load order can be important,
as one extension can be acting on values that have been transformed by a previous extension. The client comes with a set 
of pre-written plugin extensions, located in the `/extensions` package. By default (See ExtensionResolver.java) the client loads some of them,
such as VersionedRecordExtension; however, you can override this behavior on the client builder and load any
extensions you like or specify none if you do not want the ones bundled by default.

In this example, a custom extension named 'verifyChecksumExtension' is being loaded after the VersionedRecordExtension
which is usually loaded by default by itself:
```java
DynamoDbEnhancedClientExtension versionedRecordExtension = VersionedRecordExtension.builder().build();

DynamoDbEnhancedClient enhancedClient = 
    DynamoDbEnhancedClient.builder()
                          .dynamoDbClient(dynamoDbClient)
                          .extensions(versionedRecordExtension, verifyChecksumExtension)
                          .build();
```

### VersionedRecordExtension

This extension is loaded by default and will increment and track a record version number as
records are written to the database. A condition will be added to every
write that will cause the write to fail if the record version number of
the actual persisted record does not match the value that the
application last read. This effectively provides optimistic locking for
record updates, if another process updates a record between the time the
first process has read the record and is writing an update to it then
that write will fail. 

To tell the extension which attribute to use to track the record version
number tag a numeric attribute in the TableSchema:
```java
    @DynamoDbVersionAttribute
    public Integer getVersion() {...};
    public void setVersion(Integer version) {...};
```
Or using a StaticTableSchema:
```java
    .addAttribute(Integer.class, a -> a.name("version")
                                       .getter(Customer::getVersion)
                                       .setter(Customer::setVersion)
                                        // Apply the 'version' tag to the attribute
                                       .tags(versionAttribute())                         
```

### AtomicCounterExtension

This extension is loaded by default and will increment numerical attributes each time records are written to the 
database. Start and increment values can be specified, if not counters start at 0 and increments by 1. 

To tell the extension which attribute is a counter, tag an attribute of type Long in the TableSchema, here using 
standard values:
```java
    @DynamoDbAtomicCounter
    public Long getCounter() {...};
    public void setCounter(Long counter) {...};
```
Or using a StaticTableSchema with custom values:
```java
    .addAttribute(Integer.class, a -> a.name("counter")
                                       .getter(Customer::getCounter)
                                       .setter(Customer::setCounter)
                                        // Apply the 'atomicCounter' tag to the attribute with start and increment values
                                       .tags(atomicCounter(10L, 5L))                         
```

### AutoGeneratedTimestampRecordExtension

This extension enables selected attributes to be automatically updated with a current timestamp every time the item 
is successfully written to the database. One requirement is the attribute must be of `Instant` type.

This extension is not loaded by default, you need to specify it as custom extension while creating the enhanced 
client. 

To tell the extension which attribute will be updated with the current timestamp, tag the Instant attribute in
the TableSchema:
```java
    @DynamoDbAutoGeneratedTimestampAttribute
    public Instant getLastUpdate() {...}
    public void setLastUpdate(Instant lastUpdate) {...}
```

If using a StaticTableSchema:
```java
     .addAttribute(Instant.class, a -> a.name("lastUpdate")
                                        .getter(Customer::getLastUpdate)
                                        .setter(Customer::setLastUpdate)
                                        // Applying the 'autoGeneratedTimestamp' tag to the attribute
                                        .tags(autoGeneratedTimestampAttribute())
```


## Advanced table schema features
### Explicitly include/exclude attributes in DDB mapping
#### Excluding attributes
Ignore attributes that should not participate in mapping to DDB
Mark the attribute with the @DynamoDbIgnore annotation:
```java
private String internalKey;

@DynamoDbIgnore
public String getInternalKey() { return this.internalKey; }
public void setInternalKey(String internalKey) { return this.internalKey = internalKey;}
```
#### Including attributes
Change the name used to store an attribute in DBB by explicitly marking it with the
 @DynamoDbAttribute annotation and supplying a different name:
```java
private String internalKey;

@DynamoDbAttribute("renamedInternalKey")
public String getInternalKey() { return this.internalKey; }
public void setInternalKey(String internalKey) { return this.internalKey = internalKey;}
```

### Control attribute conversion
By default, the table schema provides converters for all primitive and many common Java types
through a default implementation of the AttributeConverterProvider interface. This behavior
can be changed both at the attribute converter provider level as well as for a single attribute.

You can find a list of the available converters in the 
[AttributeConverter](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/enhanced/dynamodb/AttributeConverter.html) 
interface Javadoc.

#### Provide custom attribute converter providers
You can provide a single AttributeConverterProvider or a chain of ordered AttributeConverterProviders
through the @DynamoDbBean 'converterProviders' annotation. Any custom AttributeConverterProvider must extend the AttributeConverterProvider 
interface. 

Note that if you supply your own chain of attribute converter providers, you will override
the default converter provider (DefaultAttributeConverterProvider) and must therefore include it in the chain if you wish to
use its attribute converters. It's also possible to annotate the bean with an empty array `{}`, thus
disabling the usage of any attribute converter providers including the default, in which case
all attributes must have their own attribute converters (see below).

Single converter provider:
```java
@DynamoDbBean(converterProviders = ConverterProvider1.class)
public class Customer {

}
```

Chain of converter providers ending with the default (least priority):
```java
@DynamoDbBean(converterProviders = {
   ConverterProvider1.class, 
   ConverterProvider2.class,
   DefaultAttributeConverterProvider.class})
public class Customer {

}
```

In the same way, adding a chain of attribute converter providers directly to a StaticTableSchema:
```java
private static final StaticTableSchema<Customer> CUSTOMER_TABLE_SCHEMA =
  StaticTableSchema.builder(Customer.class)
    .newItemSupplier(Customer::new)
    .addAttribute(String.class, a -> a.name("name")
                                     a.getter(Customer::getName)
                                     a.setter(Customer::setName))
    .attributeConverterProviders(converterProvider1, converterProvider2)
    .build();
```

#### Override the mapping of a single attribute
Supply an AttributeConverter when creating the attribute to directly override any
converters provided by the table schema AttributeConverterProviders. Note that you will 
only add a custom converter for that attribute; other attributes, even of the same
type, will not use that converter unless explicitly specified for those other attributes. 

Example:
```java
@DynamoDbBean
public class Customer {
    private String name;

    @DynamoDbConvertedBy(CustomAttributeConverter.class)
    public String getName() { return this.name; }
    public void setName(String name) { this.name = name;}
}
```
For StaticTableSchema:
```java
private static final StaticTableSchema<Customer> CUSTOMER_TABLE_SCHEMA =
  StaticTableSchema.builder(Customer.class)
    .newItemSupplier(Customer::new)
    .addAttribute(String.class, a -> a.name("name")
                                     a.getter(Customer::getName)
                                     a.setter(Customer::setName)
                                     a.attributeConverter(customAttributeConverter))
    .build();
```

### Changing update behavior of attributes
It is possible to customize the update behavior as applicable to individual attributes when an 'update' operation is
performed (e.g. UpdateItem or an update within TransactWriteItems).

For example, say like you wanted to store a 'created on' timestamp on your record, but only wanted its value to be
written if there is no existing value for the attribute stored in the database then you would use the 
WRITE_IF_NOT_EXISTS update behavior. Here is an example using a bean:

```java
@DynamoDbBean
public class Customer extends GenericRecord {
    private String id;
    private Instant createdOn;

    @DynamoDbPartitionKey
    public String getId() { return this.id; }
    public void setId(String id) { this.name = id; }

    @DynamoDbUpdateBehavior(UpdateBehavior.WRITE_IF_NOT_EXISTS)
    public Instant getCreatedOn() { return this.createdOn; }    
    public void setCreatedOn(Instant createdOn) { this.createdOn = createdOn; }
}
```

Same example using a static table schema:

```java
static final TableSchema<Customer> CUSTOMER_TABLE_SCHEMA =
     TableSchema.builder(Customer.class)
       .newItemSupplier(Customer::new)
       .addAttribute(String.class, a -> a.name("id")
                                         .getter(Customer::getId)
                                         .setter(Customer::setId)
                                         .tags(primaryPartitionKey()))
       .addAttribute(Instant.class, a -> a.name("createdOn")
                                          .getter(Customer::getCreatedOn)
                                          .setter(Customer::setCreatedOn)
                                          .tags(updateBehavior(UpdateBehavior.WRITE_IF_NOT_EXISTS)))
       .build();
```

### Flat map attributes from another class
If the attributes for your table record are spread across several
different Java objects, either through inheritance or composition, the
static TableSchema implementation gives you a method of flat mapping
those attributes and rolling them up into a single schema.

#### Using inheritance
To accomplish flat map using inheritance, the only requirement is that
both classes are annotated as a DynamoDb bean:

```java
@DynamoDbBean
public class Customer extends GenericRecord {
    private String name;
    private GenericRecord record;

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name;}

    public GenericRecord getRecord() { return this.record; }
    public void setRecord(GenericRecord record) { this.record = record;}
}

@DynamoDbBean
public abstract class GenericRecord {
    private String id;
    private String createdDate;

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id;}

    public String getCreatedDate() { return this.createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate;}
}

```

For StaticTableSchema, use the 'extend' feature to achieve the same effect:
```java
@Data
public class Customer extends GenericRecord {
  private String name;
}

@Data
public abstract class GenericRecord {
  private String id;
  private String createdDate;
}

private static final StaticTableSchema<GenericRecord> GENERIC_RECORD_SCHEMA =
  StaticTableSchema.builder(GenericRecord.class)
       // The partition key will be inherited by the top level mapper
      .addAttribute(String.class, a -> a.name("id")
                                        .getter(GenericRecord::getId)
                                        .setter(GenericRecord::setId)
                                        .tags(primaryPartitionKey()))
      .addAttribute(String.class, a -> a.name("created_date")
                                        .getter(GenericRecord::getCreatedDate)
                                        .setter(GenericRecord::setCreatedDate))
     .build();
    
private static final StaticTableSchema<Customer> CUSTOMER_TABLE_SCHEMA =
  StaticTableSchema.builder(Customer.class)
    .newItemSupplier(Customer::new)
    .addAttribute(String.class, a -> a.name("name")
                                      .getter(Customer::getName)
                                      .setter(Customer::setName))
    .extend(GENERIC_RECORD_SCHEMA)     // All the attributes of the GenericRecord schema are added to Customer
    .build();
```
#### Using composition

Using composition, the @DynamoDbFlatten annotation flat maps the composite class:
```java
@DynamoDbBean
public class Customer {
    private String name;
    private GenericRecord record;

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name;}

    @DynamoDbFlatten
    public GenericRecord getRecord() { return this.record; }
    public void setRecord(GenericRecord record) { this.record = record;}
}

@DynamoDbBean
public class GenericRecord {
    private String id;
    private String createdDate;

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id;}

    public String getCreatedDate() { return this.createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate;}
}
```
You can flatten as many different eligible classes as you like using the flatten annotation.
The only constraints are that attributes must not have the same name when they are being rolled
together, and there must never be more than one partition key, sort key or table name.

Flat map composite classes using StaticTableSchema:

```java
@Data
public class Customer{
  private String name;
  private GenericRecord recordMetadata;
  //getters and setters for all attributes
}

@Data
public class GenericRecord {
  private String id;
  private String createdDate;
  //getters and setters for all attributes
}

private static final StaticTableSchema<GenericRecord> GENERIC_RECORD_SCHEMA =
  StaticTableSchema.builder(GenericRecord.class)
      .addAttribute(String.class, a -> a.name("id")
                                        .getter(GenericRecord::getId)
                                        .setter(GenericRecord::setId)
                                        .tags(primaryPartitionKey()))
      .addAttribute(String.class, a -> a.name("created_date")
                                        .getter(GenericRecord::getCreatedDate)
                                        .setter(GenericRecord::setCreatedDate))
     .build();
    
private static final StaticTableSchema<Customer> CUSTOMER_TABLE_SCHEMA =
  StaticTableSchema.builder(Customer.class)
    .newItemSupplier(Customer::new)
    .addAttribute(String.class, a -> a.name("name")
                                      .getter(Customer::getName)
                                      .setter(Customer::setName))
    // Because we are flattening a component object, we supply a getter and setter so the
    // mapper knows how to access it
    .flatten(GENERIC_RECORD_SCHEMA, Customer::getRecordMetadata, Customer::setRecordMetadata)
    .build(); 
```
Just as for annotations, you can flatten as many different eligible classes as you like using the
builder pattern. 
