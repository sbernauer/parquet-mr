/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.avro;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.RecordConsumer;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.apache.parquet.avro.AvroTestUtil.array;
import static org.apache.parquet.avro.AvroTestUtil.optional;
import static org.apache.parquet.avro.AvroTestUtil.optionalField;
import static org.apache.parquet.avro.AvroTestUtil.primitive;
import static org.apache.parquet.avro.AvroTestUtil.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class TestReadWriteOldListBehavior {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    Object[][] data = new Object[][] {
        { false },  // use the new converters
        { true } }; // use the old converters
    return Arrays.asList(data);
  }

  private final boolean compat;
  private final Configuration testConf = new Configuration(false);

  public TestReadWriteOldListBehavior(boolean compat) {
    this.compat = compat;
    this.testConf.setBoolean(AvroReadSupport.AVRO_COMPATIBILITY, compat);
  }

  @Test
  public void testEmptyArray() throws Exception {
    Schema schema = new Schema.Parser().parse(
        Resources.getResource("array.avsc").openStream());

    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());
    List<Integer> emptyArray = new ArrayList<Integer>();

    try(AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<GenericRecord>(file, schema)) {
      // Write a record with an empty array.
      GenericData.Record record = new GenericRecordBuilder(schema)
        .set("myarray", emptyArray).build();
      writer.write(record);
    }

    try(AvroParquetReader<GenericRecord> reader = new AvroParquetReader<>(testConf, file)) {
      GenericRecord nextRecord = reader.read();

      assertNotNull(nextRecord);
      assertEquals(emptyArray, nextRecord.get("myarray"));
    }
  }

  @Test
  public void testEmptyMap() throws Exception {
    Schema schema = new Schema.Parser().parse(
        Resources.getResource("map.avsc").openStream());

    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());
    ImmutableMap emptyMap = new ImmutableMap.Builder<String, Integer>().build();

    try(AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<GenericRecord>(file, schema)) {
      // Write a record with an empty map.
      GenericData.Record record = new GenericRecordBuilder(schema)
        .set("mymap", emptyMap).build();
      writer.write(record);
    }

    try(AvroParquetReader<GenericRecord> reader = new AvroParquetReader<>(testConf, file)) {
      GenericRecord nextRecord = reader.read();

      assertNotNull(nextRecord);
      assertEquals(emptyMap, nextRecord.get("mymap"));
    }
  }

  @Test
  public void testMapWithNulls() throws Exception {
    Schema schema = new Schema.Parser().parse(
        Resources.getResource("map_with_nulls.avsc").openStream());

    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());
    Map<CharSequence, Integer> map = new HashMap<>();

    try(AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<GenericRecord>(file, schema)) {
      // Write a record with a null value
      map.put(str("thirty-four"), 34);
      map.put(str("eleventy-one"), null);
      map.put(str("one-hundred"), 100);

      GenericData.Record record = new GenericRecordBuilder(schema)
        .set("mymap", map).build();
      writer.write(record);
    }

    try(AvroParquetReader<GenericRecord> reader = new AvroParquetReader<>(testConf, file)) {
      GenericRecord nextRecord = reader.read();

      assertNotNull(nextRecord);
      assertEquals(map, nextRecord.get("mymap"));
    }
  }

  @Test(expected=RuntimeException.class)
  public void testMapRequiredValueWithNull() throws Exception {
    Schema schema = Schema.createRecord("record1", null, null, false);
    schema.setFields(Lists.newArrayList(
        new Schema.Field("mymap", Schema.createMap(Schema.create(Schema.Type.INT)), null, null)));

    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());

    try(AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<GenericRecord>(file, schema)) {
      // Write a record with a null value
      Map<String, Integer> map = new HashMap<String, Integer>();
      map.put("thirty-four", 34);
      map.put("eleventy-one", null);
      map.put("one-hundred", 100);

      GenericData.Record record = new GenericRecordBuilder(schema)
        .set("mymap", map).build();
      writer.write(record);
    }
  }

  @Test
  public void testMapWithUtf8Key() throws Exception {
    Schema schema = new Schema.Parser().parse(
        Resources.getResource("map.avsc").openStream());

    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());

    try(AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<GenericRecord>(file, schema)) {
      // Write a record with a map with Utf8 keys.
      GenericData.Record record = new GenericRecordBuilder(schema)
        .set("mymap", ImmutableMap.of(new Utf8("a"), 1, new Utf8("b"), 2))
        .build();
      writer.write(record);
    }

    try(AvroParquetReader<GenericRecord> reader = new AvroParquetReader<>(testConf, file)) {
      GenericRecord nextRecord = reader.read();

      assertNotNull(nextRecord);
      assertEquals(ImmutableMap.of(str("a"), 1, str("b"), 2), nextRecord.get("mymap"));
    }
  }

  @Test
  public void testAll() throws Exception {
    Schema schema = new Schema.Parser().parse(
        Resources.getResource("all.avsc").openStream());

    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());

    GenericData.Record nestedRecord = new GenericRecordBuilder(
        schema.getField("mynestedrecord").schema())
            .set("mynestedint", 1).build();

    List<Integer> integerArray = Arrays.asList(1, 2, 3);
    GenericData.Array<Integer> genericIntegerArray = new GenericData.Array<Integer>(
        Schema.createArray(Schema.create(Schema.Type.INT)), integerArray);

    GenericFixed genericFixed = new GenericData.Fixed(
        Schema.createFixed("fixed", null, null, 1), new byte[]{(byte) 65});

    List<Integer> emptyArray = new ArrayList<Integer>();
    ImmutableMap emptyMap = new ImmutableMap.Builder<String, Integer>().build();

    try(AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<>(file, schema)) {
      GenericData.Record record = new GenericRecordBuilder(schema)
        .set("mynull", null)
        .set("myboolean", true)
        .set("myint", 1)
        .set("mylong", 2L)
        .set("myfloat", 3.1f)
        .set("mydouble", 4.1)
        .set("mybytes", ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)))
        .set("mystring", "hello")
        .set("mynestedrecord", nestedRecord)
        .set("myenum", "a")
        .set("myarray", genericIntegerArray)
        .set("myemptyarray", emptyArray)
        .set("myoptionalarray", genericIntegerArray)
        .set("myarrayofoptional", genericIntegerArray)
        .set("mymap", ImmutableMap.of("a", 1, "b", 2))
        .set("myemptymap", emptyMap)
        .set("myfixed", genericFixed)
        .build();

      writer.write(record);
    }

    try(AvroParquetReader<GenericRecord> reader = new AvroParquetReader<>(testConf, file)) {
      GenericRecord nextRecord = reader.read();

      Object expectedEnumSymbol = compat ? "a" :
        new GenericData.EnumSymbol(schema.getField("myenum").schema(), "a");

      assertNotNull(nextRecord);
      assertEquals(null, nextRecord.get("mynull"));
      assertEquals(true, nextRecord.get("myboolean"));
      assertEquals(1, nextRecord.get("myint"));
      assertEquals(2L, nextRecord.get("mylong"));
      assertEquals(3.1f, nextRecord.get("myfloat"));
      assertEquals(4.1, nextRecord.get("mydouble"));
      assertEquals(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)), nextRecord.get("mybytes"));
      assertEquals(str("hello"), nextRecord.get("mystring"));
      assertEquals(expectedEnumSymbol, nextRecord.get("myenum"));
      assertEquals(nestedRecord, nextRecord.get("mynestedrecord"));
      assertEquals(integerArray, nextRecord.get("myarray"));
      assertEquals(emptyArray, nextRecord.get("myemptyarray"));
      assertEquals(integerArray, nextRecord.get("myoptionalarray"));
      assertEquals(integerArray, nextRecord.get("myarrayofoptional"));
      assertEquals(ImmutableMap.of(str("a"), 1, str("b"), 2), nextRecord.get("mymap"));
      assertEquals(emptyMap, nextRecord.get("myemptymap"));
      assertEquals(genericFixed, nextRecord.get("myfixed"));
    }
  }

  @Test
  public void testArrayWithNullValues() throws Exception {
    Schema schema = new Schema.Parser().parse(
        Resources.getResource("all.avsc").openStream());

    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());

    GenericData.Record nestedRecord = new GenericRecordBuilder(
        schema.getField("mynestedrecord").schema())
        .set("mynestedint", 1).build();

    List<Integer> integerArray = Arrays.asList(1, 2, 3);
    GenericData.Array<Integer> genericIntegerArray = new GenericData.Array<Integer>(
        Schema.createArray(Schema.create(Schema.Type.INT)), integerArray);

    GenericFixed genericFixed = new GenericData.Fixed(
        Schema.createFixed("fixed", null, null, 1), new byte[] { (byte) 65 });

    List<Integer> emptyArray = new ArrayList<Integer>();
    ImmutableMap emptyMap = new ImmutableMap.Builder<String, Integer>().build();

    Schema arrayOfOptionalIntegers = Schema.createArray(
        optional(Schema.create(Schema.Type.INT)));
    GenericData.Array<Integer> genericIntegerArrayWithNulls =
        new GenericData.Array<>(
            arrayOfOptionalIntegers,
            Arrays.asList(1, null, 2, null, 3));

    GenericData.Record record = new GenericRecordBuilder(schema)
        .set("mynull", null)
        .set("myboolean", true)
        .set("myint", 1)
        .set("mylong", 2L)
        .set("myfloat", 3.1f)
        .set("mydouble", 4.1)
        .set("mybytes", ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)))
        .set("mystring", "hello")
        .set("mynestedrecord", nestedRecord)
        .set("myenum", "a")
        .set("myarray", genericIntegerArray)
        .set("myemptyarray", emptyArray)
        .set("myoptionalarray", genericIntegerArray)
        .set("myarrayofoptional", genericIntegerArrayWithNulls)
        .set("mymap", ImmutableMap.of("a", 1, "b", 2))
        .set("myemptymap", emptyMap)
        .set("myfixed", genericFixed)
        .build();

    try (AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<>(file, schema)) {
      writer.write(record);
      fail("Should not succeed writing an array with null values");
    } catch (Exception e) {
      Assert.assertTrue("Error message should provide context and help",
        e.getMessage().contains("parquet.avro.write-old-list-structure"));
    }
  }

  @Test
  public void testAllUsingDefaultAvroSchema() throws Exception {
    File tmp = File.createTempFile(getClass().getSimpleName(), ".tmp");
    tmp.deleteOnExit();
    tmp.delete();
    Path file = new Path(tmp.getPath());

    // write file using Parquet APIs
    try(ParquetWriter<Map<String, Object>> parquetWriter = new ParquetWriter<Map<String, Object>>(file,
        new WriteSupport<Map<String, Object>>() {

      private RecordConsumer recordConsumer;

      @Override
      public WriteContext init(Configuration configuration) {
        return new WriteContext(MessageTypeParser.parseMessageType(TestAvroSchemaConverter.ALL_PARQUET_SCHEMA),
            new HashMap<String, String>());
      }

      @Override
      public void prepareForWrite(RecordConsumer recordConsumer) {
        this.recordConsumer = recordConsumer;
      }

      @Override
      public void write(Map<String, Object> record) {
        recordConsumer.startMessage();

        int index = 0;

        recordConsumer.startField("myboolean", index);
        recordConsumer.addBoolean((Boolean) record.get("myboolean"));
        recordConsumer.endField("myboolean", index++);

        recordConsumer.startField("myint", index);
        recordConsumer.addInteger((Integer) record.get("myint"));
        recordConsumer.endField("myint", index++);

        recordConsumer.startField("mylong", index);
        recordConsumer.addLong((Long) record.get("mylong"));
        recordConsumer.endField("mylong", index++);

        recordConsumer.startField("myfloat", index);
        recordConsumer.addFloat((Float) record.get("myfloat"));
        recordConsumer.endField("myfloat", index++);

        recordConsumer.startField("mydouble", index);
        recordConsumer.addDouble((Double) record.get("mydouble"));
        recordConsumer.endField("mydouble", index++);

        recordConsumer.startField("mybytes", index);
        recordConsumer.addBinary(Binary.fromReusedByteBuffer((ByteBuffer) record.get("mybytes")));
        recordConsumer.endField("mybytes", index++);

        recordConsumer.startField("mystring", index);
        recordConsumer.addBinary(Binary.fromString((String) record.get("mystring")));
        recordConsumer.endField("mystring", index++);

        recordConsumer.startField("mynestedrecord", index);
        recordConsumer.startGroup();
        recordConsumer.startField("mynestedint", 0);
        recordConsumer.addInteger((Integer) record.get("mynestedint"));
        recordConsumer.endField("mynestedint", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("mynestedrecord", index++);

        recordConsumer.startField("myenum", index);
        recordConsumer.addBinary(Binary.fromString((String) record.get("myenum")));
        recordConsumer.endField("myenum", index++);

        recordConsumer.startField("myarray", index);
        recordConsumer.startGroup();
        recordConsumer.startField("array", 0);
        for (int val : (int[]) record.get("myarray")) {
          recordConsumer.addInteger(val);
        }
        recordConsumer.endField("array", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("myarray", index++);

        recordConsumer.startField("myoptionalarray", index);
        recordConsumer.startGroup();
        recordConsumer.startField("array", 0);
        for (int val : (int[]) record.get("myoptionalarray")) {
          recordConsumer.addInteger(val);
        }
        recordConsumer.endField("array", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("myoptionalarray", index++);

        recordConsumer.startField("myarrayofoptional", index);
        recordConsumer.startGroup();
        recordConsumer.startField("list", 0);
        for (Integer val : (Integer[]) record.get("myarrayofoptional")) {
          recordConsumer.startGroup();
          if (val != null) {
            recordConsumer.startField("element", 0);
            recordConsumer.addInteger(val);
            recordConsumer.endField("element", 0);
          }
          recordConsumer.endGroup();
        }
        recordConsumer.endField("list", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("myarrayofoptional", index++);

        recordConsumer.startField("myrecordarray", index);
        recordConsumer.startGroup();
        recordConsumer.startField("array", 0);
        recordConsumer.startGroup();
        recordConsumer.startField("a", 0);
        for (int val : (int[]) record.get("myrecordarraya")) {
          recordConsumer.addInteger(val);
        }
        recordConsumer.endField("a", 0);
        recordConsumer.startField("b", 1);
        for (int val : (int[]) record.get("myrecordarrayb")) {
          recordConsumer.addInteger(val);
        }
        recordConsumer.endField("b", 1);
        recordConsumer.endGroup();
        recordConsumer.endField("array", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("myrecordarray", index++);

        recordConsumer.startField("myrecordarraywithsingleattribute", index);
        recordConsumer.startGroup();
        recordConsumer.startField("array", 0);
        recordConsumer.startGroup();
        recordConsumer.startField("a", 0);
        for (int val : (int[]) record.get("myrecordarraywithsingleattributea")) {
          recordConsumer.addInteger(val);
        }
        recordConsumer.endField("a", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("array", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("myrecordarraywithsingleattribute", index++);

        recordConsumer.startField("mymap", index);
        recordConsumer.startGroup();
        recordConsumer.startField("map", 0);
        recordConsumer.startGroup();
        Map<String, Integer> mymap = (Map<String, Integer>) record.get("mymap");
        recordConsumer.startField("key", 0);
        for (String key : mymap.keySet()) {
          recordConsumer.addBinary(Binary.fromString(key));
        }
        recordConsumer.endField("key", 0);
        recordConsumer.startField("value", 1);
        for (int val : mymap.values()) {
          recordConsumer.addInteger(val);
        }
        recordConsumer.endField("value", 1);
        recordConsumer.endGroup();
        recordConsumer.endField("map", 0);
        recordConsumer.endGroup();
        recordConsumer.endField("mymap", index++);

        recordConsumer.startField("myfixed", index);
        recordConsumer.addBinary(Binary.fromReusedByteArray((byte[]) record.get("myfixed")));
        recordConsumer.endField("myfixed", index++);

        recordConsumer.endMessage();
      }
    })) {
      Map<String, Object> record = new HashMap<String, Object>();
      record.put("myboolean", true);
      record.put("myint", 1);
      record.put("mylong", 2L);
      record.put("myfloat", 3.1f);
      record.put("mydouble", 4.1);
      record.put("mybytes", ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
      record.put("mystring", "hello");
      record.put("myenum", "a");
      record.put("mynestedint", 1);
      record.put("myarray", new int[]{1, 2, 3});
      record.put("myoptionalarray", new int[]{1, 2, 3});
      record.put("myarrayofoptional", new Integer[]{1, null, 2, null, 3});
      record.put("myrecordarraya", new int[]{1, 2, 3});
      record.put("myrecordarrayb", new int[]{4, 5, 6});
      record.put("myrecordarraywithsingleattributea", new int[]{1, 2, 3});
      record.put("mymap", ImmutableMap.of("a", 1, "b", 2));
      record.put("myfixed", new byte[]{(byte) 65});
      parquetWriter.write(record);
    }

    Schema nestedRecordSchema = Schema.createRecord("mynestedrecord", null, null, false);
    nestedRecordSchema.setFields(Arrays.asList(
        new Schema.Field("mynestedint", Schema.create(Schema.Type.INT), null, null)
    ));
    GenericData.Record nestedRecord = new GenericRecordBuilder(nestedRecordSchema)
        .set("mynestedint", 1).build();

    List<Integer> integerArray = Arrays.asList(1, 2, 3);

    Schema recordArraySchema = Schema.createRecord("array", null, null, false);
    recordArraySchema.setFields(Arrays.asList(
        new Schema.Field("a", Schema.create(Schema.Type.INT), null, null),
        new Schema.Field("b", Schema.create(Schema.Type.INT), null, null)
    ));
    GenericRecordBuilder builder = new GenericRecordBuilder(recordArraySchema);
    List<GenericData.Record> recordArray = new ArrayList<GenericData.Record>();
    recordArray.add(builder.set("a", 1).set("b", 4).build());
    recordArray.add(builder.set("a", 2).set("b", 5).build());
    recordArray.add(builder.set("a", 3).set("b", 6).build());
    GenericData.Array<GenericData.Record> genericRecordArray = new GenericData.Array<GenericData.Record>(
        Schema.createArray(recordArraySchema), recordArray);

    Schema recordArrayWithSingleAttributeSchema = Schema.createRecord("array", null, null, false);
    recordArrayWithSingleAttributeSchema.setFields(Arrays.asList(
      new Schema.Field("a", Schema.create(Schema.Type.INT), null, null)
    ));
    GenericRecordBuilder recordArrayWithSingleAttributeBuilder = new GenericRecordBuilder(recordArrayWithSingleAttributeSchema);
    List<GenericData.Record> recordArrayWithSingleAttribute = new ArrayList<>();
    recordArrayWithSingleAttribute.add(recordArrayWithSingleAttributeBuilder.set("a", 1).build());
    recordArrayWithSingleAttribute.add(recordArrayWithSingleAttributeBuilder.set("a", 2).build());
    recordArrayWithSingleAttribute.add(recordArrayWithSingleAttributeBuilder.set("a", 3).build());
    GenericData.Array<GenericData.Record> genericRecordArrayWithSingleAttribute = new GenericData.Array<>(
      Schema.createArray(recordArrayWithSingleAttributeSchema), recordArrayWithSingleAttribute);

    GenericFixed genericFixed = new GenericData.Fixed(
        Schema.createFixed("fixed", null, null, 1), new byte[] { (byte) 65 });

    // 3-level lists are deserialized with the extra layer present
    Schema elementSchema = record("list",
        optionalField("element", primitive(Schema.Type.INT)));
    GenericRecordBuilder elementBuilder = new GenericRecordBuilder(elementSchema);
    GenericData.Array<GenericData.Record> genericRecordArrayWithNullIntegers =
        new GenericData.Array<GenericData.Record>(array(elementSchema),
            Arrays.asList(
                elementBuilder.set("element", 1).build(),
                elementBuilder.set("element", null).build(),
                elementBuilder.set("element", 2).build(),
                elementBuilder.set("element", null).build(),
                elementBuilder.set("element", 3).build()
            ));

    try(AvroParquetReader<GenericRecord> reader = new AvroParquetReader<>(testConf, file)) {
      GenericRecord nextRecord = reader.read();
      assertNotNull(nextRecord);
      assertEquals(true, nextRecord.get("myboolean"));
      assertEquals(1, nextRecord.get("myint"));
      assertEquals(2L, nextRecord.get("mylong"));
      assertEquals(3.1f, nextRecord.get("myfloat"));
      assertEquals(4.1, nextRecord.get("mydouble"));
      assertEquals(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)), nextRecord.get("mybytes"));
      assertEquals(str("hello"), nextRecord.get("mystring"));
      assertEquals(str("a"), nextRecord.get("myenum"));
      assertEquals(nestedRecord, nextRecord.get("mynestedrecord"));
      assertEquals(integerArray, nextRecord.get("myarray"));
      assertEquals(integerArray, nextRecord.get("myoptionalarray"));
      assertEquals(genericRecordArrayWithNullIntegers, nextRecord.get("myarrayofoptional"));
      assertEquals(genericRecordArray, nextRecord.get("myrecordarray"));
      assertEquals(genericRecordArrayWithSingleAttribute.toString(), nextRecord.get("myrecordarraywithsingleattribute").toString());
      assertEquals(ImmutableMap.of(str("a"), 1, str("b"), 2), nextRecord.get("mymap"));
      assertEquals(genericFixed, nextRecord.get("myfixed"));
    }
  }

  /**
   * Return a String or Utf8 depending on whether compatibility is on
   */
  public CharSequence str(String value) {
    return compat ? value : new Utf8(value);
  }
}
