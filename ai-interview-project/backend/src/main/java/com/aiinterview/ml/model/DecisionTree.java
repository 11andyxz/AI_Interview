package com.aiinterview.ml.model;

import java.io.Serializable;
import java.util.*;

/**
 * Decision Tree implementation for GBRT regression.
 * Supports configurable max depth and L2 regularization.
 */
public class DecisionTree implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Node root;
    private final int maxDepth;
    private final double l2Lambda;
    private final int minSamplesLeaf;
    
    public DecisionTree(int maxDepth, double l2Lambda, int minSamplesLeaf) {
        this.maxDepth = maxDepth;
        this.l2Lambda = l2Lambda;
        this.minSamplesLeaf = minSamplesLeaf;
    }
    
    /**
     * Train the decision tree on features and residuals
     */
    public void train(List<double[]> features, List<Double> residuals) {
        if (features.size() != residuals.size()) {
            throw new IllegalArgumentException("Features and residuals must have same size");
        }
        
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            indices.add(i);
        }
        
        this.root = buildTree(features, residuals, indices, 0);
    }
    
    /**
     * Recursively build the tree
     */
    private Node buildTree(List<double[]> features, List<Double> residuals, 
                          List<Integer> indices, int depth) {
        
        // Stopping conditions
        if (depth >= maxDepth || indices.size() < minSamplesLeaf * 2) {
            return createLeafNode(residuals, indices);
        }
        
        // Find best split
        Split bestSplit = findBestSplit(features, residuals, indices);
        
        if (bestSplit == null || bestSplit.gain <= 0) {
            return createLeafNode(residuals, indices);
        }
        
        // Create internal node
        Node node = new Node();
        node.isLeaf = false;
        node.featureIndex = bestSplit.featureIndex;
        node.threshold = bestSplit.threshold;
        
        // Split indices
        List<Integer> leftIndices = new ArrayList<>();
        List<Integer> rightIndices = new ArrayList<>();
        
        for (int idx : indices) {
            if (features.get(idx)[bestSplit.featureIndex] <= bestSplit.threshold) {
                leftIndices.add(idx);
            } else {
                rightIndices.add(idx);
            }
        }
        
        // Recursively build subtrees
        node.left = buildTree(features, residuals, leftIndices, depth + 1);
        node.right = buildTree(features, residuals, rightIndices, depth + 1);
        
        return node;
    }
    
    /**
     * Create leaf node with optimal value
     */
    private Node createLeafNode(List<Double> residuals, List<Integer> indices) {
        Node leaf = new Node();
        leaf.isLeaf = true;
        
        // Compute optimal leaf value with L2 regularization
        double sum = 0.0;
        for (int idx : indices) {
            sum += residuals.get(idx);
        }
        
        // Optimal value = sum / (count + lambda)
        leaf.value = sum / (indices.size() + l2Lambda);
        
        return leaf;
    }
    
    /**
     * Find best split for a set of samples
     */
    private Split findBestSplit(List<double[]> features, List<Double> residuals, 
                                List<Integer> indices) {
        
        if (indices.isEmpty()) return null;
        
        int numFeatures = features.get(0).length;
        Split bestSplit = null;
        double bestGain = 0.0;
        
        // Try each feature
        for (int featureIdx = 0; featureIdx < numFeatures; featureIdx++) {
            
            // Sort indices by feature value
            final int fi = featureIdx;
            List<Integer> sortedIndices = new ArrayList<>(indices);
            sortedIndices.sort(Comparator.comparingDouble(idx -> features.get(idx)[fi]));
            
            // Try each split point
            for (int i = minSamplesLeaf; i < sortedIndices.size() - minSamplesLeaf; i++) {
                double threshold = features.get(sortedIndices.get(i))[featureIdx];
                
                // Skip duplicate values
                if (i > 0 && Math.abs(threshold - features.get(sortedIndices.get(i-1))[featureIdx]) < 1e-10) {
                    continue;
                }
                
                // Compute gain for this split
                double gain = computeSplitGain(residuals, sortedIndices, i);
                
                if (gain > bestGain) {
                    bestGain = gain;
                    bestSplit = new Split();
                    bestSplit.featureIndex = featureIdx;
                    bestSplit.threshold = threshold;
                    bestSplit.gain = gain;
                }
            }
        }
        
        return bestSplit;
    }
    
    /**
     * Compute gain for a split (reduction in MSE)
     */
    private double computeSplitGain(List<Double> residuals, List<Integer> sortedIndices, 
                                    int splitPoint) {
        
        double leftSum = 0.0, rightSum = 0.0;
        double leftSumSq = 0.0, rightSumSq = 0.0;
        int leftCount = 0, rightCount = 0;
        
        for (int i = 0; i < sortedIndices.size(); i++) {
            int idx = sortedIndices.get(i);
            double residual = residuals.get(idx);
            
            if (i < splitPoint) {
                leftSum += residual;
                leftSumSq += residual * residual;
                leftCount++;
            } else {
                rightSum += residual;
                rightSumSq += residual * residual;
                rightCount++;
            }
        }
        
        if (leftCount == 0 || rightCount == 0) return 0.0;
        
        // Variance before split
        double totalSum = leftSum + rightSum;
        int totalCount = leftCount + rightCount;
        double totalSumSq = leftSumSq + rightSumSq;
        double varBefore = totalSumSq / totalCount - (totalSum * totalSum) / (totalCount * totalCount);
        
        // Variance after split
        double leftVar = leftSumSq / leftCount - (leftSum * leftSum) / (leftCount * leftCount);
        double rightVar = rightSumSq / rightCount - (rightSum * rightSum) / (rightCount * rightCount);
        double varAfter = (leftCount * leftVar + rightCount * rightVar) / totalCount;
        
        // Gain = reduction in variance
        return varBefore - varAfter;
    }
    
    /**
     * Predict value for a single sample
     */
    public double predict(double[] features) {
        if (root == null) {
            throw new IllegalStateException("Tree has not been trained");
        }
        return traverse(root, features);
    }
    
    /**
     * Traverse tree to get prediction
     */
    private double traverse(Node node, double[] features) {
        if (node.isLeaf) {
            return node.value;
        }
        
        if (features[node.featureIndex] <= node.threshold) {
            return traverse(node.left, features);
        } else {
            return traverse(node.right, features);
        }
    }
    
    /**
     * Node class representing decision tree nodes
     */
    private static class Node implements Serializable {
        private static final long serialVersionUID = 1L;
        
        boolean isLeaf;
        double value;           // For leaf nodes
        int featureIndex;       // For internal nodes
        double threshold;       // For internal nodes
        Node left;
        Node right;
    }
    
    /**
     * Split class representing a potential split
     */
    private static class Split {
        int featureIndex;
        double threshold;
        double gain;
    }
}
