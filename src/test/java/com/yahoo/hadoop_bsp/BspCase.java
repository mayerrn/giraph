package com.yahoo.hadoop_bsp;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import com.yahoo.hadoop_bsp.examples.GeneratedVertexReader;

import junit.framework.TestCase;

/**
 * Extended TestCase for making setting up Bsp testing.
 */
public class BspCase extends TestCase implements Watcher {
    /** JobTracker system property */
    private final String m_jobTracker =
        System.getProperty("prop.mapred.job.tracker");
    /** Jar location system property */
    private final String m_jarLocation =
        System.getProperty("prop.jarLocation", "");
    /** Number of actual processes for the BSP application */
    private int m_numWorkers = 1;
    /** ZooKeeper list system property */
    private final String m_zkList = System.getProperty("prop.zookeeper.list");

    /**
     * Adjust the configuration to the basic test case
     */
    public final void setupConfiguration(Configuration conf) {
        conf.set("mapred.jar", getJarLocation());
        // Allow this test to be run on a real Hadoop setup
        if (getJobTracker() != null) {
            System.out.println("setup: Sending job to job tracker " +
                       getJobTracker() + " with jar path " + getJarLocation()
                       + " for " + getName());
            conf.set("mapred.job.tracker", getJobTracker());
            conf.setInt(BspJob.BSP_MAX_WORKERS, getNumWorkers());
            conf.setFloat(BspJob.BSP_MIN_PERCENT_RESPONDED, 100.0f);
            conf.setInt(BspJob.BSP_MIN_WORKERS, getNumWorkers());
        }
        else {
            System.out.println("setup: Using local job runner with " +
                               "location " + getJarLocation() + " for "
                               + getName());
            conf.setInt(BspJob.BSP_MAX_WORKERS, 1);
            conf.setFloat(BspJob.BSP_MIN_PERCENT_RESPONDED, 100.0f);
            conf.setInt(BspJob.BSP_MIN_WORKERS, 1);
            // Single node testing
            conf.setBoolean(BspJob.BSP_SPLIT_MASTER_WORKER, false);
        }
        conf.setInt(BspJob.BSP_POLL_ATTEMPTS, 5);
        conf.setInt(BspJob.BSP_POLL_MSECS, 3*1000);
        if (getZooKeeperList() != null) {
            conf.set(BspJob.BSP_ZOOKEEPER_LIST, getZooKeeperList());
        }
        // GeneratedInputSplit will generate 5 vertices
        conf.setLong(GeneratedVertexReader.READER_VERTICES, 5);
    }

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public BspCase(String testName) {
        super(testName);

    }

    /**
     * Get the number of workers used in the BSP application
     *
     * @param numProcs number of processes to use
     */
    public int getNumWorkers() {
        return m_numWorkers;
    }

    /**
     * Get the ZooKeeper list
     */
    public String getZooKeeperList() {
        return m_zkList;
    }

    /**
     * Get the jar location
     *
     * @return location of the jar file
     */
    String getJarLocation() {
        return m_jarLocation;
    }

    /**
     * Get the job tracker location
     *
     * @return job tracker location as a string
     */
    String getJobTracker() {
        return m_jobTracker;
    }

    /**
     * Get the single part file status and make sure there is only one part
     *
     * @param fs Filesystem to look for the part file
     * @param partDirPath Directory where the single part file should exist
     * @return Single part file status
     * @throws IOException
     */
    public static FileStatus getSinglePartFileStatus(FileSystem fs,
                                                     Path partDirPath)
            throws IOException {
        FileStatus[] statusArray = fs.listStatus(partDirPath);
        FileStatus singlePartFileStatus = null;
        int partFiles = 0;
        for (FileStatus fileStatus : statusArray) {
            if (fileStatus.getPath().getName().equals("part-m-00000")) {
                singlePartFileStatus = fileStatus;
            }
            if (fileStatus.getPath().getName().startsWith("part-m-")) {
                ++partFiles;
            }
        }
        if (partFiles != 1) {
            throw new ArithmeticException(
                "getSinglePartFile: Part file count should be 1, but is " +
                partFiles);
        }
        return singlePartFileStatus;
    }

    @Override
    public void setUp() {
        if (m_jobTracker != null) {
            System.out.println("Setting tasks to 3 for " + getName() +
                               " since JobTracker exists...");
            m_numWorkers = 3;
        }
        try {
            Configuration conf = new Configuration();
            FileSystem hdfs = FileSystem.get(conf);
            // Since local jobs always use the same paths, remove them
            Path oldLocalJobPaths = new Path(
                BspJob.DEFAULT_ZOOKEEPER_MANAGER_DIR);
            FileStatus [] fileStatusArr = hdfs.listStatus(oldLocalJobPaths);
            for (FileStatus fileStatus : fileStatusArr) {
                if (fileStatus.isDir() &&
                        fileStatus.getPath().getName().contains("job_local")) {
                    System.out.println("Cleaning up local job path " +
                                       fileStatus.getPath().getName());
                    hdfs.delete(oldLocalJobPaths, true);
                }
            }
            if (m_zkList == null) {
                return;
            }
            ZooKeeperExt zooKeeperExt =
                new ZooKeeperExt(m_zkList, 30*1000, this);
            List<String> rootChildren = zooKeeperExt.getChildren("/", false);
            for (String rootChild : rootChildren) {
                if (rootChild.startsWith("_hadoopBsp")) {
                    List<String> children =
                        zooKeeperExt.getChildren("/" + rootChild, false);
                    for (String child: children) {
                        if (child.contains("job_local_")) {
                            System.out.println("Cleaning up /_hadoopBsp/" +
                                               child);
                            zooKeeperExt.deleteExt(
                                "/_hadoopBsp/" + child, -1, true);
                        }
                    }
                }
            }
            zooKeeperExt.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void process(WatchedEvent event) {
        // Do nothing
    }
}
