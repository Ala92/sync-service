package com.stacksync.syncservice.test.benchmark.normal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stacksync.syncservice.db.ConnectionPool;
import com.stacksync.syncservice.db.ConnectionPoolFactory;
import com.stacksync.syncservice.db.infinispan.models.ItemMetadataRMI;
import com.stacksync.syncservice.handler.Handler;
import com.stacksync.syncservice.handler.SQLSyncHandler;
import com.stacksync.syncservice.util.Config;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TestCommit {

   private final static int DEFAULT_NUMBER_TASKS = 1;
   private final static int DEFAULT_NUMBER_COMMITS = 1;
   private final static int DEFAULT_NUMBER_WORKSPACES = 4000;
   private final static int DEFAULT_NUMBER_USERS = 4000;
   private final static int DEFAULT_NUMBER_DEVICES = 4000;

   private static final String defaultServer ="localhost:11222";

   @Option(name = "-server", usage = "ip:port or ip of the server")
   private String server = defaultServer;

   @Option(name = "-tasks", usage = "number of tasks; default="+DEFAULT_NUMBER_TASKS)
   private int nNumberTasks = DEFAULT_NUMBER_TASKS;

   @Option(name = "-commits", usage = "number of commits per task; default="+DEFAULT_NUMBER_COMMITS)
   private int numberCommits = DEFAULT_NUMBER_COMMITS;

   @Option(name = "-users", usage = "number of users; default="+DEFAULT_NUMBER_USERS)
   private int numberUsers = DEFAULT_NUMBER_USERS;

   @Option(name = "-verbose", usage = "print time for each operation; default=false")
   private boolean verbose;

   @Option(name = "-load", usage = "load phase; default=false")
   private boolean load;

   public static void main(String[] args) {
      new TestCommit().doMain(args);
   }

   private void doMain(String[] args) {
      CmdLineParser parser = new CmdLineParser(this);
      parser.setUsageWidth(80);
      try {
         parser.parseArgument(args);
      } catch( CmdLineException e ) {
         System.err.println(e.getMessage());
         parser.printUsage(System.err);
         System.err.println();
         return;
      }

      try {
         Config.loadProperties();
         Properties properties = Config.getProperties();
         properties.setProperty("infinispan_host", server);
         String datasource = Config.getDatasource();
         ConnectionPool pool = ConnectionPoolFactory.getConnectionPool(datasource);
         pool.getConnection().cleanup();
         if (load)
            populate(pool);
         else
            execute(pool);
      } catch (Exception e) {
         e.printStackTrace();
      }

      System.exit(0);

   }

   public TestCommit(){}

   public TestCommit(int numberTasks, int numberCommits, int numberUsers, boolean verbose){
      this.numberCommits = numberCommits;
      this.nNumberTasks = numberTasks;
      this.numberUsers = numberUsers;
      this.verbose = verbose;
   }

   public void populate(ConnectionPool pool) throws Exception {

      Handler handler = new SQLSyncHandler(pool);

      System.out.print("Creating " + numberUsers + " users ... ");

      // populate
      List<UUID> users = new ArrayList<>(numberUsers);
      for(int i=0; i < numberUsers; i++) {
         UUID userId = UUID.nameUUIDFromBytes(("cli" + Integer.toString(i)).getBytes());
         users.add(userId);
         handler.populate(userId);
      }

      System.out.println("done");

   }

   public List<UUID> execute(ConnectionPool pool) throws Exception {
      ExecutorService service = Executors.newFixedThreadPool(nNumberTasks);

      // list users
      List<UUID> users = new ArrayList<>(numberUsers);
      for(int i=0; i < numberUsers; i++) {
         UUID userId = UUID.nameUUIDFromBytes(("cli" + Integer.toString(i)).getBytes());
         users.add(userId);
      }

      System.out.println("Using " + numberUsers + " users");

      // we launch the tasks
      long start = System.currentTimeMillis();
      List<Future<Float>> futures = new ArrayList<>();
      for (int i=0; i< nNumberTasks; i++) {
         Handler handler = new SQLSyncHandler(pool); // new handler for each task
         CommitTask task = new CommitTask(handler, numberCommits, users, verbose);
         futures.add(service.submit(task));
      }

      float totalThroughput = 0;
      for(Future<Float> future: futures) {
         totalThroughput += future.get();
      }
      System.out.println("TotalTime=" + (System.currentTimeMillis() - start));
      System.out.println("TotalThroughput=" + totalThroughput);

      return  users;
   }

   public static List<ItemMetadataRMI> getObjectMetadata(JsonArray allFiles, UUID deviceId) {
		List<ItemMetadataRMI> metadataList = new ArrayList<>();

		for (int i = 0; i < allFiles.size(); i++) {
			JsonObject file = allFiles.get(i).getAsJsonObject();

			long fileId = file.get("file_id").getAsLong();
			long version = file.get("version").getAsLong();

			Long parentFileVersion = null;
			try {
				parentFileVersion = file.get("parent_file_version").getAsLong();
			} catch (Exception ex) {
				// ex.printStackTrace();
			}

			Long parentFileId = null;
			try {
				parentFileId = file.get("parent_file_id").getAsLong();
			} catch (Exception ex) {
				// ex.printStackTrace();
			}

			Date updated = new Date(file.get("updated").getAsLong());
			String status = file.get("status").getAsString();
			Date lastModified = new Date(file.get("lastModified").getAsLong());
			long checksum = file.get("checksum").getAsLong();
			long fileSize = file.get("fileSize").getAsLong();

			int folderInt = file.get("folder").getAsInt();
			boolean folder = folderInt == 0 ? false : true;

			String name = file.get("name").getAsString();
			String mimetype = file.get("mimetype").getAsString();
			JsonArray jChunks = file.get("chunks").getAsJsonArray(); // more
																		// optimal
			List<String> chunks = new ArrayList<String>();
			for (int j = 0; j < jChunks.size(); j++) {
				chunks.add(jChunks.get(j).getAsString());
			}

			ItemMetadataRMI object = new ItemMetadataRMI(
               fileId, version, deviceId, parentFileId, parentFileVersion, status, lastModified,
					checksum, fileSize, folder, name, mimetype, chunks);

			metadataList.add(object);
		}

		return metadataList;
	}

}
