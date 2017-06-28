package monkey;

/*
 * Java Genetic Algorithm Library (@__identifier__@).
 * Copyright (c) @__year__@ Franz Wilhelmstötter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author:
 *    Franz Wilhelmstötter (franz.wilhelmstoetter@gmx.at)
 */


import static org.jenetics.engine.EvolutionResult.toBestPhenotype;

import java.util.stream.IntStream;

import org.jenetics.CharacterChromosome;
import org.jenetics.CharacterGene;
import org.jenetics.Genotype;
import org.jenetics.Mutator;
import org.jenetics.Phenotype;
import org.jenetics.SinglePointCrossover;
import org.jenetics.StochasticUniversalSelector;
import org.jenetics.TournamentSelector;
import org.jenetics.engine.Engine;
import org.jenetics.util.CharSeq;
import org.jenetics.util.Factory;

/**
 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
 * @since 1.0
 * @version 3.5
 */
public class CharGene {

	private static final String TARGET_STRING = "jenetics tttyyz";

	private static Integer evaluate(final Genotype<CharacterGene> gt) {
		final CharSequence source = (CharSequence)gt.getChromosome();
		System.out.println("gt " + gt + " numb gnes " + gt.getNumberOfGenes() );
		int x =  IntStream.range(0, TARGET_STRING.length())
			.map(i -> source.charAt(i) == TARGET_STRING.charAt(i) ? 1 : 0)
			.sum();
		System.out.println("x " + x );
		return x;
	}

	public static void main(String[] args) throws Exception {
		final CharSeq chars = CharSeq.of("a-z");
		final Factory<Genotype<CharacterGene>> gtf = Genotype.of(
			new CharacterChromosome(chars, TARGET_STRING.length())
		);

		final Engine<CharacterGene, Integer> engine = Engine
			.builder(CharGene::evaluate, gtf)
			.populationSize(100)
			.survivorsSelector(new StochasticUniversalSelector<>())
			.offspringSelector(new TournamentSelector<>(5))
			.alterers(
				new Mutator<>(0.1),
				new SinglePointCrossover<>(0.5))
			.build();

		final Phenotype<CharacterGene, Integer> result = engine.stream()
			.limit(40)
			.collect(toBestPhenotype());

		System.out.println(result);
	}

}