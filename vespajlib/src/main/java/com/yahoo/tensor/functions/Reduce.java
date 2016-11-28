package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.tensor.MapTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The <i>reduce</i> tensor operation returns a tensor produced from the argument tensor where some dimensions 
 * are collapsed to a single value using an aggregator function.
 *
 * @author bratseth
 */
public class Reduce extends PrimitiveTensorFunction {

    public enum Aggregator { avg, count, prod, sum, max, min; }

    private final TensorFunction argument;
    private final List<String> dimensions;
    private final Aggregator aggregator;

    /** Creates a reduce function reducing aLL dimensions */
    public Reduce(TensorFunction argument, Aggregator aggregator) {
        this(argument, aggregator, Collections.emptyList());
    }

    /** Creates a reduce function reducing a single dimension */
    public Reduce(TensorFunction argument, Aggregator aggregator, String dimension) {
        this(argument, aggregator, Collections.singletonList(dimension));
    }

    /**
     * Creates a reduce function.
     * 
     * @param argument the tensor to reduce
     * @param aggregator the aggregator function to use
     * @param dimensions the list of dimensions to remove. If an empty list is given, all dimensions are reduced,
     *                   producing a dimensionless tensor (a scalar).
     * @throws IllegalArgumentException if any of the tensor dimensions are not present in the input tensor
     */
    public Reduce(TensorFunction argument, Aggregator aggregator, List<String> dimensions) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(aggregator, "The aggregator cannot be null");
        Objects.requireNonNull(dimensions, "The dimensions cannot be null");
        this.argument = argument;
        this.aggregator = aggregator;
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    public TensorFunction argument() { return argument; }

    @Override
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Reduce must have 1 argument, got " + arguments.size());
        return new Reduce(arguments.get(0), aggregator, dimensions);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Reduce(argument.toPrimitive(), aggregator, dimensions);
    }

    @Override
    public String toString(ToStringContext context) {
        return "reduce(" + argument.toString(context) + ", " + aggregator + commaSeparated(dimensions) + ")";
    }
    
    private String commaSeparated(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String element  : list)
            b.append(", ").append(element);
        return b.toString();
    }

    @Override
    public Tensor evaluate(EvaluationContext context) {
        Tensor argument = this.argument.evaluate(context);

        if ( ! dimensions.isEmpty() && ! argument.dimensions().containsAll(dimensions))
            throw new IllegalArgumentException("Cannot reduce " + argument + " over dimensions " + 
                                               dimensions + ": Not all those dimensions are present in this tensor");

        if (dimensions.isEmpty() || dimensions.size() == argument.dimensions().size())
            return reduceAll(argument);
        
        // Reduce dimensions
        Set<String> reducedDimensions = new HashSet<>(argument.dimensions());
        reducedDimensions.removeAll(dimensions);
        
        // Reduce cells
        Map<TensorAddress, ValueAggregator> aggregatingCells = new HashMap<>();
        for (Map.Entry<TensorAddress, Double> cell : argument.cells().entrySet()) {
            TensorAddress reducedAddress = reduceDimensions(cell.getKey(), reducedDimensions);
            aggregatingCells.putIfAbsent(reducedAddress, ValueAggregator.ofType(aggregator));
            aggregatingCells.get(reducedAddress).aggregate(cell.getValue());
        }
        ImmutableMap.Builder<TensorAddress, Double> reducedCells = new ImmutableMap.Builder<>();
        for (Map.Entry<TensorAddress, ValueAggregator> aggregatingCell : aggregatingCells.entrySet())
            reducedCells.put(aggregatingCell.getKey(), aggregatingCell.getValue().aggregatedValue());
        return new MapTensor(reducedDimensions, reducedCells.build());
    }
    
    private TensorAddress reduceDimensions(TensorAddress address, Set<String> reducedDimensions) {
        return TensorAddress.fromSorted(address.elements().stream()
                                                          .filter(e -> reducedDimensions.contains(e.dimension()))
                                                          .collect(Collectors.toList()));
    }
    
    private Tensor reduceAll(Tensor argument) {
        ValueAggregator valueAggregator = ValueAggregator.ofType(aggregator);
        for (Double cellValue : argument.cells().values())
            valueAggregator.aggregate(cellValue);
        return new MapTensor(ImmutableMap.of(TensorAddress.empty, valueAggregator.aggregatedValue()));
    }
    
    private static abstract class ValueAggregator {
        
        public static ValueAggregator ofType(Aggregator aggregator) {
            switch (aggregator) {
                case avg : return new AvgAggregator();
                case count : return new CountAggregator();
                case prod : return new ProdAggregator();
                case sum : return new SumAggregator();
                case max : return new MaxAggregator();
                case min : return new MinAggregator();
                default: throw new UnsupportedOperationException("Aggregator " + aggregator + " is not implemented");
            }
                
        }

        /** Add a new value to those aggregated by this */
        public abstract void aggregate(double value);
        
        /** Returns the value aggregated by this */
        public abstract double aggregatedValue();
        
    }
    
    private static class AvgAggregator extends ValueAggregator {

        private int valueCount = 0;
        private double valueSum = 0.0;
        
        @Override
        public void aggregate(double value) {
            valueCount++;
            valueSum+= value;
        }

        @Override
        public double aggregatedValue() { 
            return valueSum / valueCount;
        }

    }

    private static class CountAggregator extends ValueAggregator {

        private int valueCount = 0;

        @Override
        public void aggregate(double value) {
            valueCount++;
        }

        @Override
        public double aggregatedValue() {
            return valueCount;
        }

    }

    private static class ProdAggregator extends ValueAggregator {

        private double valueProd = 1.0;

        @Override
        public void aggregate(double value) {
            valueProd *= value;
        }

        @Override
        public double aggregatedValue() {
            return valueProd;
        }

    }

    private static class SumAggregator extends ValueAggregator {

        private double valueSum = 0.0;

        @Override
        public void aggregate(double value) {
            valueSum += value;
        }

        @Override
        public double aggregatedValue() {
            return valueSum;
        }

    }

    private static class MaxAggregator extends ValueAggregator {

        private double maxValue = Double.MIN_VALUE;

        @Override
        public void aggregate(double value) {
            if (value > maxValue)
                maxValue = value;
        }

        @Override
        public double aggregatedValue() {
            return maxValue;
        }

    }

    private static class MinAggregator extends ValueAggregator {

        private double minValue = Double.MAX_VALUE;

        @Override
        public void aggregate(double value) {
            if (value < minValue)
                minValue = value;
        }

        @Override
        public double aggregatedValue() {
            return minValue;
        }

    }

}
