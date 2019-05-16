/* This file is part of the OWL API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright 2014, The University of Manchester
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.mansyntax.ManchesterOWLSyntaxParser;

import javax.annotation.Nonnull;
import java.util.Set;

class DLQueryParser {

    @Nonnull
    private final OWLOntology rootOntology;
    @Nonnull
    private final BidirectionalShortFormProvider bidiShortFormProvider;

    /**
     * Constructs a DLQueryParser using the specified ontology and short form
     * provider to map entity IRIs to short names.
     *
     * @param rootOntology
     *        The root ontology. This essentially provides the domain vocabulary
     *        for the query.
     * @param shortFormProvider
     *        A short form provider to be used for mapping back and forth
     *        between entities and their short names (renderings).
     */
    public DLQueryParser(@Nonnull OWLOntology rootOntology, @Nonnull ShortFormProvider shortFormProvider) {
        this.rootOntology = rootOntology;
        OWLOntologyManager manager = rootOntology.getOWLOntologyManager();
        Set<OWLOntology> importsClosure = rootOntology.getImportsClosure();
        // Create a bidirectional short form provider to do the actual mapping.
        // It will generate names using the input
        // short form provider.
        bidiShortFormProvider = new BidirectionalShortFormProviderAdapter(manager, importsClosure, shortFormProvider);
    }

    /**
     * Parses a class expression string to obtain a class expression.
     *
     * @param classExpressionString
     *        The class expression string
     * @return The corresponding class expression if the class expression string
     *         is malformed or contains unknown entity names.
     */
    @Nonnull
    public OWLClassExpression parseClassExpression(@Nonnull String classExpressionString) {
        // Set up the real parser
        ManchesterOWLSyntaxParser parser = OWLManager.createManchesterParser();
        parser.setStringToParse(classExpressionString);
        parser.setDefaultOntology(rootOntology);
        // Specify an entity checker that wil be used to check a class
        // expression contains the correct names.
        OWLEntityChecker entityChecker = new ShortFormEntityChecker(bidiShortFormProvider);
        parser.setOWLEntityChecker(entityChecker);
        // Do the actual parsing
        return parser.parseClassExpression();
    }
}