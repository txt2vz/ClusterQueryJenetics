package cluster

import org.apache.lucene.document.Document
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.search.TotalHitCountCollector

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import index.IndexInfo

@groovy.transform.CompileStatic
@groovy.transform.TypeChecked
public class ClusterFitness{

	private Map <Query, Integer> queryMap  = [:]
	private double positiveScoreTotal = 0.0
	private double negativeScoreTotal = 0.0
	private double fraction = 0.0
	private double baseFitness = 0.0
	private double scorePlus1000 = 0.0
	private double scoreOnly = 0.0

	private int positiveHits = 0
	private int negativeHits = 0
	private int duplicateCount = 0
	private int lowSubqHits = 0
	private int coreClusterPenalty = 0
	private int totalHits = 0
	private int missedDocs = 0
	private int zeroHitsCount = 0
	private boolean isDummy = false
	private boolean emptyQueries = false

	private int treePenalty=0;
	private int graphPenalty=0

	private final int hitsPerPage = IndexInfo.indexReader.maxDoc()
	private final int coreClusterSize = 40

	public double getFitness(){
		return baseFitness;
	}

	String queryShort (){
		def s="queryMap.size ${queryMap.size()} \n"
		queryMap.keySet().eachWithIndex {Query q, int index ->
			if (index>0) s+='\n';
			s +=  "ClusterQuery: $index :  ${queryMap.get(q)}  ${q.toString(IndexInfo.FIELD_CONTENTS)}"
		}
		return s
	}

	public void generationStats(long generation){
		println "Gereration $generation ${queryShort()}"

		printf "PosHits: %d NegHits: %d PosScr: %.2f NegScr: %.2f ScrOnly: %.2f ScPlus1000: %.2f coreClstPen: %d lowSubqHits(AND): %d fit: %.2f zeoHitsCount: %d  \n",
				positiveHits, negativeHits, positiveScoreTotal, negativeScoreTotal, scoreOnly, scorePlus1000, coreClusterPenalty, lowSubqHits, baseFitness, zeroHitsCount
		println "TotalHits: " + totalHits + " Total Docs: " + IndexInfo.indexReader.maxDoc()  +  " fraction: " + fraction +
				" baseFit: " + baseFitness + " missedDocs: " + missedDocs + " duplicate count " + duplicateCount//+ " log(misseddocs): " +   Math.log(cf.missedDocs)
	}

	public void setClusterFitness ( List <BooleanQuery.Builder> bqbArray){
		assert bqbArray.size() == IndexInfo.NUMBER_OF_CLUSTERS

		Map <Query, Integer> qMap = new HashMap<Query, Integer>()
		Set allHits = [] as Set

		bqbArray.eachWithIndex {BooleanQuery.Builder bqb, index ->

			Query q = bqb.build()

			def otherdocIdSet= [] as Set
			def otherQueries = bqbArray - bqb

			BooleanQuery.Builder bqbOthers = new BooleanQuery.Builder();
			otherQueries.each {obqb ->
				bqbOthers.add(obqb.build(),  BooleanClause.Occur.SHOULD)
			}
			Query otherBQ = bqbOthers.build()

			TopDocs otherTopDocs = IndexInfo.indexSearcher.search(otherBQ, hitsPerPage)
			ScoreDoc[] hitsOthers = otherTopDocs.scoreDocs;
			hitsOthers.each {ScoreDoc otherHit -> otherdocIdSet << otherHit.doc }

			TopDocs docs = IndexInfo.indexSearcher.search(q, hitsPerPage)
			ScoreDoc[] hits = docs.scoreDocs;
			qMap.put(q,hits.size())

			if (hits.size()<1)   zeroHitsCount ++

			hits.eachWithIndex {ScoreDoc d, int position ->
				allHits << d.doc

				if (otherdocIdSet.contains(d.doc)){
					negativeHits++;
					negativeScoreTotal = negativeScoreTotal + d.score
					if (position < coreClusterSize ){
						//heavy penalty
						//def reverseRank = coreClusterSize - position
						//fitness.coreClusterPenalty +=reverseRank
						coreClusterPenalty++
					}
				}
				else {
					positiveHits++
					positiveScoreTotal =  positiveScoreTotal + d.score
				}
			}
		}

		queryMap = qMap.asImmutable()
		//	println "in fitness qmap $qMap"

		scoreOnly = positiveScoreTotal - (negativeScoreTotal * 2)
		totalHits = allHits.size()
		fraction = totalHits / IndexInfo.indexReader.maxDoc()
		missedDocs = IndexInfo.indexReader.maxDoc()  - allHits.size()

		//fitness must be positive for ECJ - most runs start with large negative score
		final int minScore = 1000
		scorePlus1000 = (scoreOnly < -minScore) ? 0 : scoreOnly + minScore

		final int negIndicators =
				//major penalty for query returning nothing or empty query
				(zeroHitsCount * 100) + coreClusterPenalty + duplicateCount + lowSubqHits + 1;

		baseFitness =  //(scorePlus1000 / negIndicators) * fraction * fraction
		          scoreOnly;
	} 

	@TypeChecked(TypeCheckingMode.SKIP)
	public void queryStats (int job, long gen, int popSize){
		//	public void queryStats (int job, int gen, int popSize){
		String messageOut=""
		FileWriter resultsOut = new FileWriter("results/clusterResultsF1.txt", true)
		resultsOut <<"  ***** Job: $job Gen: $gen PopSize: $popSize Noclusters: ${IndexInfo.NUMBER_OF_CLUSTERS}  pathToIndex: ${IndexInfo.pathToIndex}  *********** ${new Date()} ***************************************************** \n"

		def f1list = [], precisionList =[], recallList =[]
		queryMap.keySet().eachWithIndex {q, index ->

			TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
			IndexInfo.indexSearcher.search(q, collector);
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			def qString = q.toString(IndexInfo.FIELD_CONTENTS)

			println "***********************************************************************************"
			messageOut = "ClusterQuery: $index hits: ${hits.length} Query:  $qString \n"
			println messageOut
			resultsOut << messageOut

			//map of categories (ground truth) and their frequencies
			def catsFreq=[:]
			hits.eachWithIndex{ScoreDoc h, int i ->
				int docId = h.doc;
				def scr = h.score
				Document d = IndexInfo.indexSearcher.doc(docId);
				String catName = d.get(IndexInfo.FIELD_CATEGORY_NAME)
				int n = catsFreq.get((catName)) ?: 0
				catsFreq.put((catName), n + 1)

				//view top 5 results
				//				if (i <5){
				//					messageOut = "$i path ${d.get(IndexInfo.FIELD_PATH)} cat name: $catName "
				//					println messageOut
				//					resultsOut << messageOut + '\n'
				//				}
			}
			println "Gen: $gen ClusterQuery: $index catsFreq: $catsFreq for query: $qString "

			//find the category with maximimum returned docs for this query
			def catMax = catsFreq?.max{it?.value} ?:0

			println "catsFreq: $catsFreq cats max: $catMax "

			//purity measure - check this is correct?
			//def purity = (hits.size()==0) ? 0 : (1 / hits.size())  * catMax.value
			//println "purity:  $purity"

			if (catMax !=0){
				TotalHitCountCollector totalHitCollector  = new TotalHitCountCollector();
				TermQuery catQ = new TermQuery(new Term(IndexInfo.FIELD_CATEGORY_NAME,
						catMax.key));
				IndexInfo.indexSearcher.search(catQ, totalHitCollector);
				int categoryTotal = totalHitCollector.getTotalHits();
				messageOut = "categoryTotal: $categoryTotal for category: $catQ \n"
				println messageOut
				resultsOut << messageOut

				double recall = catMax.value / categoryTotal;
				double precision = catMax.value / hits.size()
				double f1 = (2 * precision * recall) / (precision + recall);

				f1list << f1
				precisionList << precision
				recallList << recall
				messageOut = "f1: $f1 recall: $recall precision: $precision"
				println messageOut
				resultsOut << messageOut + "\n"
				//resultsOut << "Purity: $purity Job: $job \n"
			}
		}

		double averageF1 = (f1list) ? f1list.sum()/ IndexInfo.NUMBER_OF_CLUSTERS : 0
		double averageRecall = (recallList) ? recallList.sum()/ IndexInfo.NUMBER_OF_CLUSTERS : 0
		double averagePrecision =(precisionList) ? precisionList.sum()/ IndexInfo.NUMBER_OF_CLUSTERS :0
		messageOut ="***  TOTALS:   *****   f1list: $f1list averagef1: :$averageF1  ** average precision: $averagePrecision average recall: $averageRecall"
		println messageOut

		resultsOut << "TotalHits: $totalHits Total Docs:  ${IndexInfo.instance.indexReader.maxDoc()} \n"
		resultsOut << "PosHits: $positiveHits NegHits: $negativeHits PosScore: $positiveScoreTotal NegScore: $negativeScoreTotal baseFitness: $baseFitness \n"
		resultsOut << messageOut + "\n"
		resultsOut << "************************************************ \n \n"

		resultsOut.flush()
		resultsOut.close()

		boolean appnd =  true//job!=0
		FileWriter fcsv = new FileWriter("results/resultsCluster.csv", appnd)
		Formatter csvOut = new Formatter(fcsv);
		//if (!appnd){
			//final String fileHead = "gen, job, popSize, fitness, averageF1, averagePrecision, averageRecall, query" + '\n';
			//	csvOut.format("%s", fileHead)
		//}
				csvOut.format(
						"%s, %s, %s, %.3f, %.3f, %.3f, %.3f, %s",
						gen,
						job,
						popSize,
						baseFitness,
						averageF1,
		     			averagePrecision,
						averageRecall,
						queryForCSV(job) );

		csvOut.flush();
		csvOut.close()
	}

	private String queryForCSV (int job){
		def s="Job: $job "
		queryMap.keySet().eachWithIndex {q, index ->
			s += "ClusterQuery " + index + ": " + queryMap.get(q) + " " + q.toString(IndexInfo.FIELD_CONTENTS) + " ## "
		}
		return s + '\n'
	}
}
