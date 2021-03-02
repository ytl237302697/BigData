package com.Fuxi.homeWork;

import com.Fuxi.group08.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author ytl
 * @date 2021/3/2 15:44
 */
public class HomeWorkDriver {

    public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);

        job.setJarByClass(HomeWorkDriver.class);
        job.setMapperClass(HomeWorkMapper.class);
        job.setReducerClass(HomeWorkReduce.class);

        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(NullWritable.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(IntWritable.class);


        FileInputFormat.setInputPaths(job,new Path("C:\\Users\\YTL\\Desktop\\task1\\HomeWork\\input"));
        FileOutputFormat.setOutputPath(job,new Path("C:\\Users\\YTL\\Desktop\\task1\\HomeWork\\output"));


        boolean flag = job.waitForCompletion(true);
        System.exit(flag?0:1);
    }
}
