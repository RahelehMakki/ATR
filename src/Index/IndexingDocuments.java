package Index;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import PreProcess.TweetPreProcess;
import Util.DirectoryFileUtil;


public class IndexingDocuments {
	
	private Analyzer analyzer;
	private Directory dir;
	private IndexWriter writer;
	
	public Directory indexDocuments(String corpusPath) {
		addDocuments(corpusPath);
		return dir; 
	}
	
	private void addDocuments(String corpusPath) {
		initializeLucene();
		DirectoryFileUtil dirUtil = new DirectoryFileUtil();
		File[] myFiles = dirUtil.returnLisOfFiles(corpusPath,"");
		
		try {
			for (File f:myFiles) {
				
				File inFile = new File(corpusPath + f.getName());
				FileInputStream fis = new FileInputStream(inFile);	    
			    
				byte[] data = new byte[(int)inFile.length()];
			    fis.read(data);
			    fis.close();
			    String text = new String(data, "UTF-8");
			    
			    String processedText = text.replaceAll("[!,:\";.#/?(><)]@+\'", " ");
			    processedText = processedText.replaceAll("[\\s]+", " ");
			    processedText = processedText.trim();
			    
			    addDoc(f.getName().toLowerCase(), processedText.toLowerCase());
			    
			}
			writer.close();
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}
	
	public Directory getDirectory() {
		return dir;
	}

	private void addDoc(String Id,String text) throws IOException {
	    Document doc = new Document();
	    
	    doc.add(new Field("Id", Id, Field.Store.YES, Field.Index.NO));
	    doc.add(new Field("text", text, Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
	    
	    writer.addDocument(doc);
	  }
	
	public void initializeLucene(){
		TweetPreProcess.init();
		try {
			analyzer = new WhitespaceAnalyzer(Version.LUCENE_34);		
			dir = new RAMDirectory();
			IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_34, analyzer);
			writer = new IndexWriter(dir, config);
		}
		
		catch (Exception e){		
			e.printStackTrace();
		}
	}
}
