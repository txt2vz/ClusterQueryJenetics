package index

import java.nio.file.Path
import java.nio.file.Paths

import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.search.TotalHitCountCollector
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory

/**
 * Singleton class to store index information.
 * Set the path to the lucene index here
 */

@groovy.transform.TypeChecked
@groovy.transform.CompileStatic
@Singleton
class IndexInfo {

	// Lucene field names
	public static final String FIELD_CATEGORY_NAME = 'category',
	FIELD_CONTENTS = 'contents',
	FIELD_PATH = 'path',
	FIELD_TEST_TRAIN = 'test_train',
	FIELD_CATEGORY_NUMBER = 'categoryNumber';

	static final int NUMBER_OF_CLUSTERS =  3 , NUMBER_OF_CATEGORIES = 10
	static final IndexReader indexReader
	public static final IndexSearcher indexSearcher

	static final String pathToIndex =
	//   'indexes/R10'
	//     'indexes/NG20'
	//	 'indexes/crisis3FireBombFloodL6.6'
	// 'indexes/classic4_500L6'
	//	 'indexes/20NG5WindowsmiscForsaleHockeySpaceChristianL6'
	'indexes/20NG3SpaceHockeyChristianL6'
	
	// set the index
	static {
		Path path = Paths.get(pathToIndex)
		Directory directory = FSDirectory.open(path)
		indexReader = DirectoryReader.open(directory)
		indexSearcher = new IndexSearcher(indexReader);
	}
	static BooleanQuery trainDocsInCategoryFilter, otherTrainDocsFilter, testDocsInCategoryFilter, otherTestDocsFilter;
	static int totalTrainDocsInCat, totalTestDocsInCat, totalOthersTrainDocs, totalTestDocs;

	final TermQuery trainQ = new TermQuery(new Term(
	FIELD_TEST_TRAIN, 'train'));
	final TermQuery testQ = new TermQuery(new Term(
	FIELD_TEST_TRAIN, 'test'));

	// the categoryNumber of the current category
	static String categoryNumber='0'

	//Query to return documents in the current category based on categoryNumber
	static TermQuery catQ;

	//get hits for a particular query using filter (e.g. a particular category)
	public static int getQueryHitsWithFilter(IndexSearcher searcher, Query filter, Query q ) {
		TotalHitCountCollector collector = new TotalHitCountCollector();
		BooleanQuery.Builder  bqb = new BooleanQuery.Builder();
		bqb.add(q, BooleanClause.Occur.MUST)
		bqb.add(filter, BooleanClause.Occur.FILTER)
		searcher.search(bqb.build(), collector);
		return collector.getTotalHits();
	}

	//get the category_name for the current category
	public static String getCategoryName (){
		TopScoreDocCollector collector = TopScoreDocCollector.create(1)
		indexSearcher.search(catQ, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs

		String categoryName
		hits.each {ScoreDoc h ->
			Document d = indexSearcher.doc(h.doc)
			categoryName = d.get(FIELD_CATEGORY_NAME)
		}
		return categoryName
	}

	//set the filters and totals for the index
	public void setIndex()  {
		catQ = new TermQuery(new Term(FIELD_CATEGORY_NUMBER,
				categoryNumber));
		println "Index info catQ: $catQ"

		BooleanQuery.Builder bqb = new BooleanQuery.Builder()
		bqb.add(catQ, BooleanClause.Occur.FILTER)
		bqb.add(trainQ, BooleanClause.Occur.FILTER)
		trainDocsInCategoryFilter = bqb.build();

		bqb = new BooleanQuery.Builder()
		bqb.add(catQ, BooleanClause.Occur.FILTER)
		bqb.add(testQ, BooleanClause.Occur.FILTER)
		testDocsInCategoryFilter = bqb.build();

		bqb = new BooleanQuery.Builder()
		bqb.add(catQ, BooleanClause.Occur.MUST_NOT)
		bqb.add(trainQ, BooleanClause.Occur.FILTER)
		otherTrainDocsFilter = bqb.build();

		bqb = new BooleanQuery.Builder()
		bqb.add(catQ, BooleanClause.Occur.MUST_NOT)
		bqb.add(testQ, BooleanClause.Occur.FILTER)
		otherTestDocsFilter = bqb.build();

		TotalHitCountCollector collector  = new TotalHitCountCollector();
		indexSearcher.search(trainDocsInCategoryFilter, collector);
		totalTrainDocsInCat = collector.getTotalHits();

		collector  = new TotalHitCountCollector();
		indexSearcher.search(testDocsInCategoryFilter, collector);
		totalTestDocsInCat = collector.getTotalHits();

		collector  = new TotalHitCountCollector();
		indexSearcher.search(otherTrainDocsFilter, collector);
		totalOthersTrainDocs = collector.getTotalHits();

		collector  = new TotalHitCountCollector();
		indexSearcher.search(trainQ, collector);
		int totalTrain = collector.getTotalHits();

		collector  = new TotalHitCountCollector();
		indexSearcher.search(testQ, collector);
		totalTestDocs = collector.getTotalHits();

		println "IndexInfo:- CategoryNumber: $categoryNumber Total train in cat: $totalTrainDocsInCat  Total others tain: $totalOthersTrainDocs   Total test in cat : $totalTestDocsInCat  "
	}
}