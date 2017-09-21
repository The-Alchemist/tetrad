/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.annotation;

import edu.cmu.tetrad.data.DataType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * Sep 20, 2017 12:32:15 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradScoreAnnotations extends AbstractTetradAnnotations<Score> {

    private static final TetradScoreAnnotations INSTANCE = new TetradScoreAnnotations();

    protected final List<AnnotatedClassWrapper<Score>> nameWrappers;
    protected final Map<DataType, List<AnnotatedClassWrapper<Score>>> dataTypeNameWrappers;

    private TetradScoreAnnotations() {
        super("edu.cmu.tetrad.algcomparison.score", Score.class);

        nameWrappers = annotatedClasses.stream()
                .map(e -> new AnnotatedClassWrapper<>(e.getAnnotation().name(), e))
                .sorted()
                .collect(Collectors.toList());

        dataTypeNameWrappers = nameWrappers.stream()
                .collect(Collectors.groupingBy(e -> e.annotatedClass.getAnnotation().dataType()));

        // merge continuous datatype with mixed datatype
        List<AnnotatedClassWrapper<Score>> mergeList = Stream.concat(dataTypeNameWrappers.get(DataType.Continuous).stream(), dataTypeNameWrappers.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        dataTypeNameWrappers.put(DataType.Continuous, mergeList);

        // merge discrete datatype with mixed datatype
        mergeList = Stream.concat(dataTypeNameWrappers.get(DataType.Discrete).stream(), dataTypeNameWrappers.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        dataTypeNameWrappers.put(DataType.Discrete, mergeList);
    }

    public static TetradScoreAnnotations getInstance() {
        return INSTANCE;
    }

    public List<AnnotatedClassWrapper<Score>> getNameWrappers() {
        return Collections.unmodifiableList(nameWrappers);
    }

    public List<AnnotatedClassWrapper<Score>> getNameAttributes(DataType dataType) {
        return (dataType == null)
                ? Collections.EMPTY_LIST
                : Collections.unmodifiableList(dataTypeNameWrappers.get(dataType));
    }

}