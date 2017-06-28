package index

import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.MultiFields
import org.apache.lucene.index.PostingsEnum
import org.apache.lucene.index.Term
import org.apache.lucene.index.Terms
import org.apache.lucene.index.TermsEnum
import org.apache.lucene.search.DocIdSetIterator
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.similarities.ClassicSimilarity
import org.apache.lucene.search.similarities.TFIDFSimilarity
import org.apache.lucene.util.BytesRef

/**
 * Return a list of termqueries likely to be useful for building boolean queries for classification or clustering
 * Terms should be in order of their likely usefulness in query building 
 * @author Laurie 
 */


public class ImportantTerms  {

	public final static int SPAN_FIRST_MAX_END = 300;
	private final static int MAX_TERMQUERYLIST_SIZE = 300;

	private final IndexSearcher indexSearcher= IndexInfo.indexSearcher;
	private final IndexReader indexReader = indexSearcher.indexReader
	private Terms terms
	private TermsEnum termsEnum
	private Set<String> stopSet= //StopSet.smallStopSet   
	                             StopSet.getStopSetFromFile()

	public static void main(String[] args){
		IndexInfo.instance.categoryNumber = '2'
		IndexInfo.instance.setIndex()
		def iw = new ImportantTerms()
		//	iw.getF1TermQueryList()
		iw.getTFIDFTermQueryList()
	}

	public ImportantTerms() {
		terms = MultiFields.getTerms(indexReader, IndexInfo.FIELD_CONTENTS)
		termsEnum = terms.iterator();
	}

	//screen terms likely to be ineffective
	private boolean isUsefulTerm(Term t) {
		int df = indexReader.docFreq(t)
		def word = t.text()

		return (
				df > 2
				&& !stopSet.contains(word)
				&& !word.contains("'")
				&& word.length() > 1
				&& word.charAt(0).isLetter()
				//  && !word.contains(".")
				)
	}

	/**
	 * create a set of words based on F1 measure of the term when used to classify current category
	 */
	public TermQuery[] getF1TermQueryList(){

		println "Important words terms.getDocCount: ${terms.getDocCount()}"
		println "Important words terms.size ${terms.size()}"

		BytesRef termbr
		def termQueryMap = [:]

		while((termbr = termsEnum.next()) != null) {

			Term t = new Term(IndexInfo.FIELD_CONTENTS, termbr);
			if ( isUsefulTerm(t) ){

				Query tq = new TermQuery(t)
				final int positiveHits = IndexInfo.getQueryHitsWithFilter(indexSearcher,IndexInfo.trainDocsInCategoryFilter, tq)
				final int negativeHits = IndexInfo.getQueryHitsWithFilter(indexSearcher,IndexInfo.otherTrainDocsFilter, tq)
				double F1 = classify.Effectiveness.f1(positiveHits, negativeHits,
						IndexInfo.totalTrainDocsInCat)

				if (F1 > 0.02) {
					termQueryMap += [(tq): F1]
				}
			}
		}

		termQueryMap= termQueryMap.sort{-it.value}
		println "termQueryMap: $termQueryMap"
		TermQuery[] termQueryList = termQueryMap.keySet().take(MAX_TERMQUERYLIST_SIZE).asImmutable()
		println "f1 map size: ${termQueryMap.size()}  termQuerylist size: ${termQueryList.size()}  termQuerylist: $termQueryList"
		return termQueryList
	}

	public TermQuery[] getTFIDFTermQueryList(){

		println "Important terms terms.getDocCount: ${terms.getDocCount()}"
		def termQueryMap = [:]
		BytesRef termbr;

		while((termbr = termsEnum.next()) != null) {

			Term t = new Term(IndexInfo.FIELD_CONTENTS, termbr);
			if (isUsefulTerm(t)){

				long indexDf = indexReader.docFreq(t);
				int docCount = indexReader.numDocs()

				//for lucene 5 : TFIDFSimilarity tfidfSim = new DefaultSimilarity()
				TFIDFSimilarity tfidfSim = new ClassicSimilarity()
				PostingsEnum docsEnum = termsEnum.postings(MultiFields.getTermDocsEnum(indexReader, IndexInfo.FIELD_CONTENTS, termbr ))
				double tfidfTotal=0

				if (docsEnum != null) {
					while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
						double tfidf = tfidfSim.tf(docsEnum.freq()) * tfidfSim.idf(docCount, indexDf)
						tfidfTotal +=tfidf
					}
				}
				termQueryMap+= [(new TermQuery(t)) : tfidfTotal]
			}
		}

		termQueryMap= termQueryMap.sort{a, b -> a.value <=> b.value}
		TermQuery[] termQueryList = termQueryMap.keySet().take(MAX_TERMQUERYLIST_SIZE)
		println "tfidf map size: ${termQueryMap.size()}  termQuerylist size: ${termQueryList.size()}  termQuerylist: $termQueryList"
		return termQueryList
	}
}