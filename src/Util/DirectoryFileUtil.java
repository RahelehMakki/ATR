package Util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;

public class DirectoryFileUtil { 
	
	public void makeNewDirectory(String dirPathName){
		File folder = new File(dirPathName);		
		if (!folder.exists()) {
			folder.mkdir();
		}
	}

	public File[] returnLisOfFiles(String path, final String pattern) {
		File dir = new File(path);
	
		File[] listOfFiles = dir.listFiles(new FilenameFilter() 
		{ 
			public boolean accept(File dir, String filename)
             { 
				return filename.endsWith(pattern); 
			}
		});
		return listOfFiles;
	}
	
	public HashSet<String> loadStopWords() {
		HashSet<String> stopWords = new HashSet<String>();
		
		try {
			FileInputStream fin = new FileInputStream("./data/stop.txt");
			DataInputStream din = new DataInputStream(fin);
			BufferedReader bin = new BufferedReader(new InputStreamReader(din));
			
			String text = "";
			
			while ((text=bin.readLine())!=null){			
				stopWords.add(text.toLowerCase());
			}
			bin.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return stopWords;
	}
	
	public void clearDir(String dirPath) 
	{
		File dir = new File(dirPath);
		
		File[] listOfFiles = dir.listFiles(new FilenameFilter() { 
            public boolean accept(File dir, String filename)
                 { return filename.endsWith(""); }
		});
		if (listOfFiles!=null) {
			for (File f:listOfFiles) {
				if (f.exists()) {
				f.delete();
				}
			}
		}
	}
	public HashMap<String, String> loadHashMap(String fileName) {
		HashMapUtil<String, String> reader = new HashMapUtil<String, String>(){	
		    protected HashMap<String, String> newMap() {
		        return new HashMap<String, String>(); 
		    }
		    protected void doThings(String data, HashMap<String, String> resultmap){
		        String []parts = data.split("\t");
		        resultmap.put(parts[0],parts[1]);
		        
		    }
		};
		File myFile = new File(fileName);
		HashMap<String, String> res = reader.readAllLogsIntoMap(myFile);
		return res;
	}
	
	public void copyFile(String sourceFileName, String destFileName) {
		File f = new File(sourceFileName);
		try {
			FileInputStream fis = new FileInputStream(sourceFileName);					    
	    
			byte[] data = new byte[(int)f.length()];
			fis.read(data);
			fis.close();
	    
			String text = new String(data, "UTF-8");
			
			FileWriter fout = new FileWriter(destFileName);
			BufferedWriter bout = new BufferedWriter(fout);
			
			bout.write(text);
			
			bout.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	public void copyDir(String origDir, String destDir) {
		File dir = new File(origDir);
		File[] listOfFiles = dir.listFiles(new FilenameFilter() { 
            public boolean accept(File dir, String filename)
                 { return filename.endsWith(""); }
		} );
	 	for (File f:listOfFiles) {				
			try {
				FileInputStream fis = new FileInputStream(f);					    
		    
				byte[] data = new byte[(int)f.length()];
				fis.read(data);
				fis.close();
		    
				String text = new String(data, "UTF-8");				
				FileWriter fout = new FileWriter(destDir + f.getName());
				BufferedWriter bout = new BufferedWriter(fout);				
				bout.write(text);
				
				bout.close();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	 }

	public void checkDir(String fileName, boolean failIfNotExist) {
		File folder = new File(fileName);		
		if (!folder.exists()) {
			System.out.println("Directory/File " + fileName + " is missing\n");
			System.exit(0);
		}
		
	}
}
