package com.Fuxi.homeWork;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * @author ytl
 * @date 2021/3/2 15:42
 */
public class HomeWorkReduce extends Reducer<IntWritable, NullWritable, IntWritable, IntWritable> {
    IntWritable v = new IntWritable();
    int id = 0;

    @Override
    protected void reduce(IntWritable key, Iterable<NullWritable> values, Context context) throws IOException, InterruptedException {
        for (NullWritable value : values) {
            v.set(++id);
           context.write(v, key);
        }
    }
}
