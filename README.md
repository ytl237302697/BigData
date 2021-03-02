# BigData
# 一、序列化

​	主要是通过网络通信传输数据时把对象持久化到文件，需要把（对象）序列化成（二进制的结构）

**Hadoop为什么要建立自己自己的序列化格式而不适用Java自带的serializable？**	

​	在Hadoop中，集群中多个节点的进程间通信是通过RPC（远程过程调用：Remote Procedure Call）实现，

​	RPC将消息序列化为二进制流发送到远程节点，远程节点再将接收到的二进制数据反序列化为原始的消息

​	特点：①紧凑：数据更紧凑，能充分利用网络带宽资源

​               ②快速：序列化和反序列化的性能开销更低

| Java   | Hadoop Writable |
| ------ | --------------- |
| String | Text            |
| int    | IntWritable     |
| long   | LongWritable    |



# 二、MapReduce编程规范

- Mapper:

1. 用户自定义一个Mapper类继承Hadoop的Mapper类
2. 用户的输入数据是KV对的形式
3. Map阶段的业务逻辑定义在map()方法中
4. Mapper的输出数据是kv对的形式
5. **map()方法是对输入的一个KV对调用一次**

- Reducer

1. 用户自定义Reducer类要继承Hadoop的Reducer
2. Reducer的输入数据类型对应Mapper的输出数据类型
3. Reducer的业务逻辑写在Reduce()方法中
4. **Reduce()方法是对相同的一组KV对调用执行一次**

- Driver

1. 获取配置文件对象
2. 获取Job的实例对象
3. 设置Jar的本地路径
4. 设置Mapper、Reducer类
5. 设置Map输出的KV数据类型
6. 设置最终的输出数据类型
7. 设置job处理的输入、输出路径
8. 提交任务



MR编程技巧总结：

- 结合业务设计的Map输出的K和V，利用K相同则去往同一个Reduce的特点
- map()方法中获取的知识一行文本数据尽量不做聚合运算
- reduce()方法的参数要清楚含义



# 三、Writable序列化接口



基本序列化类型往往不能满足所有需求，在Hadoop框架内部自定义一个Bean对象，那么该对象需要实现Writable接口

**步骤**

1. 必须实现Writable接口
2. 反序列化时，需要反射调用空参构造，必须有空参构造
3. 重写序列化方法
4. 重写反序列化方法
5. 反序列化的字段顺序必须跟序列化一致
6. 方便展示数据结构，按需重写ToString()方法
7. 如果自定义Bean对象需要在放在Mapper输出KV中的K,则该对象还要实现Comparable接口，因为MapReduce中的Shuffle过程要求key必须能排序	



# 四、MapReduce原理分析

### 3.1MapTask运行机制

MapTask流程

![image-20210220141523127](C:\Users\YTL\AppData\Roaming\Typora\typora-user-images\image-20210220141523127.png)

1. **按K进行分区后，按K进行排序**
2. MapTask阶段所有的排序都是针对map输出的kv的key进行排序
3. 读取数据组件InputFormat（默认TextInputFormat）会通过getSplits方法对输入目录中，文件进行逻辑切片规划得到splits，有多少个split就对应启动多少个MapTask。split与block的关系默认是一对一
4. 将输入文件且分为splits之后，由RecordReader（默认LineRecordReader）进行读取，以\n作为分隔符，读取一行数据，返回<key,value>。key表示每行首字符偏移值，value表示这一行文本内容
5. 读取split返回<key,value>，进入用户自己继承的Mapper类中，执行用户重写的map函数，RecordReader读取一行这里调用一次
6. map逻辑完之后，将map的每条结果通过context.write进行collect数据收集。在collect中，会先对其进行分区处理，默认使用HashPartitioner



### 3.2MapTask并行度（MapTask的数量）

1. MapTask的并行度决定Map阶段的任务处理并发度，从而影响到整个Job的处理速度

2. MapTask并行度决定机制

   **数据块**：Block是HDFS物理上把数据分成一块一块

   **切片(split**):数据切片知识在逻辑上对输入进行分片，并不会在磁盘上将其切分成片进行存储。一个分片(split)对应一个MapTask任务

   **分片大小默认等于Block的大小**：Spllitsize = BlockSize = 128M

   **切片的计算方式：按照文件逐个计算**

   A文件：0-128M,128-256M,256-300M

   B文件：0-100M

   总共是4个split，MapTask并行度=4

   **在大数据分布式计算框架中：移动计算也不要移动数据，移动数据的成本很高，移动计算很简单**

   **MapTask不是并行度越高越好**：如果一个文件比128M大不到10%，也会被当成一个split来对待，而不是多个split。MR框架在并行运算同时也会消耗更多资源，并行度越高资源消耗也就越高，假设129M文件分成两个分片，一个128M，一个1,对于1M的切片的MapTask来说，太浪费资源

### 3.3ReduceTask运行机制

![image-20210222211407368](H:%5C%E5%AD%A6%E4%B9%A0%5C01Hadoop%E6%A0%B8%E5%BF%83%E5%8F%8A%E7%94%9F%E6%80%81%E5%9C%88%E6%8A%80%E6%9C%AF%E6%A0%88%5Cimage-20210222211407368.png)

**ReduceTask是拉取所有map输出结果文件固定某个分区的数据**

**对于数据只要进入同一个分区 最终一定会去往同一个reduceTask**

1. Copy阶段：ReduceTask从各个MapTsk上远程拷贝一片数据，并针对某一片数据，如果大小超过一定阈值，则溢出到磁盘上，否则直接放在内存中
2. Merge阶段：在远程拷贝数据的同时，ReduceTask启动了两个后台线程对内存和磁盘上的文件进行合并，以防止内存使用过多或磁盘上文件过多
3. Sort阶段：按照MapTask语义，用户编写reduce()函数输入数据是按key进行聚集的一组数据，为了将key相同的数据聚在一起，Hadoop采用了基于排序的策略。由于各个MapTask以及实现对自己的处理结果进行了局部排序，因此ReduceTask只需对所有数据进行一次归并排序即可
4. Reduce阶段：reduce()函数将计算结果写到HDFS上

### 3.4ReduceTask并行度

MapTask并行度取决于split的数量，而split大小默认是blockSize，所以块的数量约等于分片的数量

ReduceTask的并行度同样影响整个Job的执行并发度和执行效率，但ReduceTask的并行度可以手动设置



1. reducetask=0，表示没有reduce阶段，输出文件和maptask数量保持一致
2. reducetask数量不设置默认就是一个，输出文件数量为1个
3. 数据分布不均匀，可能在Reduce阶段产生数据倾斜（某个reducetask处理的数据量远远大于其他节点）

### 3.5shuffle机制

![image-20210222213158953](H:%5C%E5%AD%A6%E4%B9%A0%5C01Hadoop%E6%A0%B8%E5%BF%83%E5%8F%8A%E7%94%9F%E6%80%81%E5%9C%88%E6%8A%80%E6%9C%AF%E6%A0%88%5Cimage-20210222213158953.png)

1. **MapReduce框架中最关键的一个流程-Shuffleshuffle：**

   洗牌、发牌--（核心机制：数据分区、排序、分组、combine、合并等过程）

2. **自定义分区**

- 自定义继承Partitioner，重写getPartition()方法
- 在Driver驱动中，指定使用自定义Partitioner
- 在Driver驱动中，要根据自定义Partitioner的逻辑设置相应数量的ReduceTask数量

**总结**

- 自定义分区器时，最好保证分区数量与ReduceTask数量保持一致
- 如果分区数量不止1个，但是ReduceTask数量1个，此时只会输出一个文件
- 如果reduceTask数量大于分区数量，会输出多个空文件
- 如果reduceTask数量小于分区数量，可能会报错



# 五、MapReduce中的排序

### 4.1Combiner(对Map的输出进行局部汇总)

1. combiner组件的父类是Reducer
2. combiner意义就是对每一个maptask的输出进行局部汇总，以减小网络传输量
3. 应用的前提是不能影响业务逻辑，combiner的输出kv应该跟reducer的输入一致

### 4.2WritableComparable(全排序)

1. 自定义对象作为Map的key输出时，需要实现WritableComparable接口，排序：重写compareTo（）方法，序列化和反序列化
2. 默认reduceTask数量为1个
3. 对于全局排序需要保证只有一个reduceTask



### 4.3GroupingComparator(分区排序)

1. 决定哪些数据作为一组，调用一次reduce的逻辑，默认是每个不同的key，作为多个不同的组，每个组调用一次reduce逻辑




# 六、MapReduce Join

### 5.1ReduceJoin

- 数据聚合功能在reduce端完成，reduce端并行度一般不高，所以执行效率存在隐患
- 相同positionId的数据去往同一个分区，如果数据本身存在不平衡，会造成大数据中的数据倾斜问题



### 5.2MapJoin



### 5.3数据倾斜解决方案

- 数据倾斜：大量的相同key被partition分配到一个分区里
- 绝大多数task执行得都非常快，但个别task执行的极慢甚至失败



以MR为例：

​	第一个阶段：对key增加随机数

​	第二个阶段：去掉key的随机数并聚合



# 七、MapReduce读取和输出数据

## 6.1InputFormat

inputformat是Mapreduce用来读取数据的类

常见子类包括：

- TextInputFormat（普通文本文件，MR框架默认的读取实现类型）
- KeyValueTextInputFormat（读取一行文本数据按照指定分隔符，把数据封装为kv类型）
- NLineInputFormat（读取数据按照行数进行划分分片）
- CombineTextInputFormat（合并小文件，避免启动过多的MapTask）
- 自定义InputFormat

### **1CombineTextInputFormat切片原理**

（虚拟存储过程和切片过程）

MR框架默认的TextInputFormat切片直机制按文件划分切片，文件无论多小，都是单独一个切片，然后由一个MapTask处理，如果有大量小文件，就对应的会生成并启动大量的MapTask，而每个MapTask处理的数据量很小，大量时间浪费在从初始化资源收回等阶段。导致资源利用率不高

CombineTextInputFormat用于小文件过多的场景，它可以将多个小文件从逻辑上划分成一个切片，多个小文件就可以交给一个MapTask处理，提高资源利用率	

### 2自定义InputFormat

MapReduce在处理小文件时效率非常低，但是又难免处理大量小文件的时候，可以自定义InputFomat实现小文件的合并

**原理**

将多个小文件合并成一个SequenceFile文件（是Hadoop用来存储二进制形式key-value的文件格式），SequenceFile里面存储着多个文件，存储的形式为 **key（文件路径+名称）**，**value(文件内容)**,

泛型为 Text,BytesWritable

**思路**

1. 定义一个自定义类继承FileInputFormat
2. 重写isSplitable()指定为不可切分，重写createRecordReader()方法，创建自己的RecordReader对象
3. 改变默认读取数据方式，实现一次读取一整个文件夹的文件作为kv输出
4. Driver指定使用的InputFormat类型

## 6.2OutputFormat

是MapReduce输出数据的父类，所有mapreduce的数据输出都实现了outputformat抽象类

- TextOutputFormat

默认的输出格式是TextOutputFormat，把每条记录写为文本行，它的键和值可以是任意类型，因为TextOutputformat调用toString()方法他他们转换为字符串

- SequenceFileOutputFormat

将SequenceFileOutputFormat输出作为后续MapReduce任务的输入，他的格式紧凑，容易被压缩

# 八、shuffle阶段数据的压缩机制

数据压缩有两大好处，节约磁盘空间，加速数据在网络和磁盘上的传输

![image-20210226105636094](H:%5C%E5%AD%A6%E4%B9%A0%5C01Hadoop%E6%A0%B8%E5%BF%83%E5%8F%8A%E7%94%9F%E6%80%81%E5%9C%88%E6%8A%80%E6%9C%AF%E6%A0%88%5C%E7%AC%94%E8%AE%B0%5C%E7%AC%94%E8%AE%B0.assets%5Cimage-20210226105636094.png)

##### 压缩位置

- Map输入端压缩

使用压缩文件作为Map的输入数据，无需显示指定编解码方式，Hadoop会自动检查文件扩展名，如果压缩方式匹配，Hadoop就会选择合适的编解码方式对文件进行压缩和解压

- Map输出端压缩

Shuffle是Hadoop MR过程中资源消耗最多的阶段，如果有数据量过大造成网络传输速度缓慢，可以考虑使用压缩

- Reduce端输出压缩

输出的结果数据使用压缩能减少存储的数据量，降低所需磁盘空间， 作为第二个MR的输入时可以复用压缩

##### 压缩配置方式

- 在驱动类Driver中通过Configuration直接设置使用的压缩方式，可以开启Map输出和Reduce输出压缩

```
设置map阶段压缩
Configuration configuration = new Configuration();
configuration.set("mapreduce.map.output.compress","true");
configuration.set("mapreduce.map.output.compress.codec","org.apache.hadoop.i
o.compress.SnappyCodec");
设置reduce阶段的压缩
configuration.set("mapreduce.output.fileoutputformat.compress","true");
configuration.set("mapreduce.output.fileoutputformat.compress.type","RECORD"
);
configuration.set("mapreduce.output.fileoutputformat.compress.codec","org.ap
ache.hadoop.io.compress.SnappyCodec");


//针对SequenceFileOutput的压缩
 SequenceFileOutputFormat.setOutputCompressorClass(job, DefaultCodec.class);
//压缩类型：record压缩
SequenceFileOutputFormat.setOutputCompressionType(job,SequenceFile.CompressionType.RECORD);
```

- 配置mapred-site.xml(修改后分发到集群其它节点，重启Hadoop集群),此种方式对运行在集群的
  所有MR任务都会执行压缩

```
<property>   
	<name>mapreduce.output.fileoutputformat.compress</name>
	<value>true</value>
</property>
<property>    
	<name>mapreduce.output.fileoutputformat.compress.type</name>
	<value>RECORD</value>
</property>
<property>    
	<name>mapreduce.output.fileoutputformat.compress.codec</name>
	<value>org.apache.hadoop.io.compress.SnappyCodec</value>
</property>
```



# 九、MR调优及二次开发

## 1. Job执行三原则

- 充分利用集群资源
- reduce阶段尽量放在一轮
- 每个task的执行时间要合理

### 1.1原则一 充分利用集群资源

Job运行时，尽量让所有的节点都有任务处理，这样能尽量保证集群资源被充分利用，任务的并发度达到最大。可以通过调整处理的数据量大小，以及调整map和reduce个数来实现

- Reduce个数的控制使用"mapreduce.job.reduces"
- Map个数取决于使用哪种InputFormat,默认的TextFileInputFormat将根据block的个数来分配map数(一个block 一个map)

### 1.2原则二 ReduceTask并发调整

避免出现以下场景

- 观察job如果大多数reducetask在第一轮完成后，剩下很少甚至一个reducetask刚开始运行，这种情况下，这个reducetask的执行时间将决定了该job的运行时间，可以考虑减少reduce个数
- 观察job的执行情况如果是maptask运行完成后，只有个别节点有reducetask在运行，这时候集群没有得到充分利用，需要增加reduce的并行度以便每个节点都有任务处理

### 1.2原则三 Task执行时间要合理

- 一个Job中，每个maptask或reducetask的执行时间只有几秒钟，这就意味着这个job的大部分时间消耗在task的调度和进程启动停止上了，因此可以考虑增加每个task处理的数据大小，建议一个task处理时加为1分钟





