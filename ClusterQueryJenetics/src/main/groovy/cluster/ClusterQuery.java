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

		final int popSize = 256;
		final long maxGen = 100; 

		final Factory<Genotype<IntegerGene>> gtf = Genotype.of(

				new IntegerChromosome(-1, 50, 9));

		final Engine<IntegerGene, Double> engine = Engine.builder(ClusterQuery::evaluate, gtf).populationSize(popSize)
				// .survivorsSelector(new
				// StochasticUniversalSelector<>()).offspringSelector(new
				// TournamentSelector<>(5))
				.survivorsSelector(new TournamentSelector<>(3)).offspringSelector(new TournamentSelector<>(3))
				.alterers(new Mutator<>(0.2), new SinglePointCrossover<>(0.7)).build();

		final Phenotype<IntegerGene, Double> result = engine.stream().limit(maxGen).peek(ind -> {
			// System.out.println("Gen: " + ind.getGeneration() + " phen " +
			// ind.getBestPhenotype());
			Genotype<IntegerGene> g = ind.getBestPhenotype().getGenotype();
			
	
			//cf2.generationStats(ind.getGeneration());
			cf(g).generationStats(ind.getGeneration()); 
			System.out.println();
		}).collect(toBestPhenotype());

		System.out.println("Final result " + result);
		Genotype<IntegerGene> g = result.getGenotype();
		ClusterFitness cfResult = cf(g);
		System.out.println("cluster fit result " + cfResult.queryShort());
		cfResult.queryStats(0, result.getGeneration(), popSize);
		System.out.println();
	}
}