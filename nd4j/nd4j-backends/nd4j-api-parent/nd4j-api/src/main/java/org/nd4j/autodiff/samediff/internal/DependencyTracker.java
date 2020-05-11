package org.nd4j.autodiff.samediff.internal;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Dependenci tracker. See {@link AbstractDependencyTracker} for details
 *
 * @param <T> For a dependency X -> Y, Y has type T
 * @param <D> For a dependency X -> Y, X has type D
 */
@Slf4j
public class DependencyTracker<T, D> extends AbstractDependencyTracker<T,D> {

    @Override
    protected Map<T, ?> newTMap() {
        return new HashMap<>();
    }

    @Override
    protected Set<T> newTSet() {
        return new HashSet<>();
    }

    @Override
    protected String toStringT(T t) {
        return t.toString();
    }

    @Override
    protected String toStringD(D d) {
        return d.toString();
    }
}
