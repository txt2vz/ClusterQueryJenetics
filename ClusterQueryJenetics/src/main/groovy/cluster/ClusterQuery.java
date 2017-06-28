package cluster; 

import static org.jenetics.engine.EvolutionResult.toBestPhenotype;

import java.util.stream.IntStream;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.jenetics.CharacterChromosome;
import org.jenetics.CharacterGene;
import org.jenetics.Genotype;
import org.jenetics.IntegerChromosome;
import org.jenetics.IntegerGene;
import org.jenetics.Mutator;
import org.jenetics.Phenotype;
import org.jenetics.SinglePointCrossover;
import org.jenetics.StochasticUniversalSelector;
import org.jenetics.TournamentSelector;
import org.jenetics.engine.Engine;
import org.jenetics.util.CharSeq;
import org.jenetics.util.Factory;

import index.ImportantTerms;
import index.IndexInfo;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class ClusterQuery {

	private final static TermQuery[] termQueryArray = new ImportantTerms().getTFIDFTermQueryList();

	private static double evaluate(final Genotype<IntegerGene> gt) {

		ClusterFitness cfit = cf(gt);
		return cfit.getFitness();
	}

	private static ClusterFitness cf(final Genotype<IntegerGene> gt) {
		List<BooleanQuery.Builder> bqbList = QueryListFromChromosome
				.getORQueryList((IntegerChromosome) gt.getChromosome(0), termQueryArray);

		ClusterFitness clusterFitness = new ClusterFitness();
		clusterFitness.setClusterFitness(bqbList);

		return clusterFitness;
	}

	public static void main(String[] args) throws Exception {
		IntStream.range(0, 4).forEach(job ->
				new ClusterQuery(job)
			);			
	}	
	
	public ClusterQuery(int job) {

		final int popSize = 500;
		final long maxGen = 1000; 

		final Factory<Genotype<IntegerGene>> gtf = Genotype.of(

				new IntegerChromosome(-1, 80, 15));

		final Engine<IntegerGene, Double> engine = Engine.builder(ClusterQuery::evaluate, gtf).populationSize(popSize)
				// .survivorsSelector(new
				// StochasticUniversalSelector<>()).offspringSelector(new
				// TournamentSelector<>(5))
				.survivorsSelector(new TournamentSelector<>(3)).offspringSelector(new TournamentSelector<>(3))
				.alterers(new Mutator<>(0.2), new SinglePointCrossover<>(0.7)).build();

		final Phenotype<IntegerGene, Double> result = engine.stream().limit(maxGen).peek(ind -> {
	
			Genotype<IntegerGene> g = ind.getBestPhenotype().getGenotype();		
			cf(g).generationStats(ind.getGeneration()); 
			System.out.println();
		}).collect(toBestPhenotype());

		System.out.println("Final result job " + job + " " + result);
		Genotype<IntegerGene> g = result.getGenotype();
		ClusterFitness cfResult = cf(g);
		System.out.println("cluster fit result " + cfResult.queryShort());
		cfResult.queryStats(job, result.getGeneration(), popSize);
		System.out.println();
	}
}