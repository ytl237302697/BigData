package com.Fuxi.homeWork;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

/**
 * @author ytl
 * @date 2021/3/2 15:40
 */
public class HomeWorkMapper extends Mapper<LongWritable, Text, IntWritable,NullWritable> {

    IntWritable k = new IntWritable();
    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        Integer num = Integer.parseInt(value.toString());
        k.set(num);
        context.write(k,NullWritable.get());
    }
}
