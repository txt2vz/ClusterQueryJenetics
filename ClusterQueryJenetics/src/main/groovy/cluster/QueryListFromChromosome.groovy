package cluster

import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.TotalHitCountCollector
import org.apache.lucene.search.spans.SpanFirstQuery
import org.apache.lucene.search.spans.SpanTermQuery
import org.jenetics.IntegerChromosome

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import index.ImportantTerms
import index.IndexInfo

@groovy.transform.CompileStatic
@groovy.transform.TypeChecked
class QueryListFromChromosome {

	public static List <BooleanQuery.Builder> getORQueryList(IntegerChromosome intc, TermQuery[] importantTerms) {

		//list of boolean queries
		List <BooleanQuery.Builder> bqbL = []
		// set of genes - for duplicate checking
		Set genes = [] as Set

		intc.toArray().eachWithIndex {int gene, int index ->
			int clusterNumber =  index % IndexInfo.NUMBER_OF_CLUSTERS
			bqbL[clusterNumber] = bqbL[clusterNumber] ?: new BooleanQuery.Builder()

			if (gene < importantTerms.size() && gene >= 0 && genes.add(gene)){
				bqbL[clusterNumber].add(importantTerms[gene],BooleanClause.Occur.SHOULD)
			}
		}
		return bqbL
	}
}