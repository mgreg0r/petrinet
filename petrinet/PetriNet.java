package petrinet;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class PetriNet<T> {

    private Map<T, Integer> tokens;
    private ConcurrentLinkedQueue<WaitingQuery<T>> queue = new ConcurrentLinkedQueue<>();

    // Mutex for protecting tokens access
    private Semaphore mutex = new Semaphore(1);


    // Ignoring fair parameter, as PetriNet's behavior in false case is not specified
    public PetriNet(Map<T, Integer> initial, boolean fair) {
        tokens = initial;
    }

    public Set<Map<T, Integer>> reachable(Collection<Transition<T>> transitions) {
        Map<T, Integer> tokensCopy;
        try {
            mutex.acquire();
            tokensCopy = new HashMap<>(tokens);
            mutex.release();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        Set<Map<T, Integer>> result = new HashSet<>();
        result.add(tokensCopy);
        dfs(tokensCopy, transitions, result);
        return result;
    }

    public Transition<T> fire(Collection<Transition<T>> transitions) throws InterruptedException {
        mutex.acquire();
        Transition<T> transition = getFirstEnabledTransition(transitions);
        if(transition == null) {
            WaitingQuery<T> query = new WaitingQuery<>(transitions);
            queue.add(query);
            mutex.release();
            try {
                query.mutex.acquire();
            } catch(InterruptedException e) {
                if(query.interrupt()) {
                    queue.remove(query);
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
            transition = getFirstEnabledTransition(transitions);
        }

        fire(transition);
        tryWakeUp();
        return transition;
    }

    private Transition<T> getFirstEnabledTransition(Collection<Transition<T>> transitions) {
        for(Transition<T> transition : transitions) {
            if(isTransitionEnabled(transition)) {
                return transition;
            }
        }

        return null;
    }

    private boolean isTransitionEnabled(Transition<T> transition) {
        return isTransitionEnabled(tokens, transition);
    }

    private void fire(Transition<T> transition) {
        tokens = fire(tokens, transition);
    }

    private void tryWakeUp() {
        for(WaitingQuery<T> query : queue) {
            if(getFirstEnabledTransition(query.transitions) != null) {
                if(query.resume()) {
                    queue.remove(query);
                    return;
                }
            }
        }

        mutex.release();
    }

    private void dfs(Map<T, Integer> state, Collection<Transition<T>> transitions, Set<Map<T, Integer>> reachedStates) {
        for(Transition<T> transition : transitions) {
            if(isTransitionEnabled(state, transition)) {
                Map<T, Integer> nextState = fire(state, transition);
                if(!reachedStates.contains(nextState)) {
                    reachedStates.add(nextState);
                    dfs(nextState, transitions, reachedStates);
                }
            }
        }
    }

    private boolean isTransitionEnabled(Map<T, Integer> state, Transition<T> transition) {
        for(Map.Entry<T, Integer> input : transition.input.entrySet()) {
            if(state.getOrDefault(input.getKey(), 0) < input.getValue()) {
                return false;
            }
        }

        for(T place : transition.inhibitor) {
            if(state.getOrDefault(place, 0) != 0) {
                return false;
            }
        }

        return true;
    }

    private Map<T, Integer> fire(Map<T, Integer> state, Transition<T> transition) {
        HashMap<T, Integer> nextState = new HashMap<>(state);
        for(Map.Entry<T, Integer> input : transition.input.entrySet()) {
            int newValue = nextState.get(input.getKey()) - input.getValue();
            if(newValue == 0) {
                nextState.remove(input.getKey());
            } else {
                nextState.put(input.getKey(), newValue);
            }
        }

        for(T place : transition.reset) {
            if(nextState.containsKey(place)) {
                nextState.remove(place);
            }
        }

        for(Map.Entry<T, Integer> output : transition.output.entrySet()) {
            nextState.put(output.getKey(), nextState.getOrDefault(output.getKey(), 0) + output.getValue());
        }

        return nextState;
    }

    public int getTokens(T place) {
        int result;

        mutex.acquireUninterruptibly();
        result = tokens.getOrDefault(place, 0);
        mutex.release();

        return result;
    }

}
