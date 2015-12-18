package Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public abstract class HashMapUtil <K, V>
{

	 protected abstract HashMap<K, V> newMap(); 
	 protected abstract void doThings(String data, HashMap<K, V> resultMap);

	 public HashMap<K, V> readAllLogsIntoMap(File file){
	      if (!file.exists()){
	          return newMap();
	      }
	      BufferedReader reader = null;
	      FileReader fileReader = null;
	      String data = null;
	      HashMap <K, V> resultMap = newMap();
	      try {
	         fileReader = new FileReader(file);
	         reader = new BufferedReader(fileReader);
	         while ((data = reader.readLine()) != null){
	             doThings(data, resultMap);
	         }
	     } catch(Exception e){

	     }
	     finally{
	         try{
	             if (reader != null) reader.close();
	             if (fileReader != null) fileReader.close();
	         } catch(IOException ioe){

	         }
	     }
	     return resultMap;
	 }
}

