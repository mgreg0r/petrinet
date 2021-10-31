package alternator;

import petrinet.PetriNet;
import petrinet.Transition;

import java.util.*;

public class Main {

    private static final String[] IDS = new String[] {"A", "B", "C"};

    public static void main(String[] args) {
        PetriNet<String> pnet = new PetriNet<>(Collections.emptyMap(), true);

        checkSafety(pnet);

        ArrayList<Thread> threads = new ArrayList<>();
        for(String id : IDS) {
            Thread t = createWorkerThread(id, pnet);
            threads.add(t);
            t.start();
        }

        try {
            Thread.sleep(30000);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for(Thread t : threads) {
            t.interrupt();
        }

    }

    private static Thread createWorkerThread(String id, PetriNet<String> pnet) {
        return new Thread(() -> {
            try {
                while(true) {
                    pnet.fire(Collections.singletonList(acquireTransition(Thread.currentThread().getName())));
                    System.out.print(Thread.currentThread().getName());
                    System.out.print(".");
                    pnet.fire(Collections.singletonList(releaseTransition(Thread.currentThread().getName())));
                }

            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, id);
    }

    private static String getCurrentPlace(String id) {
        return "cur" + id;
    }

    private static String getLastPlace(String id) {
        return "last" +id;
    }

    private static List<String> getOtherIds(String id) {
        ArrayList<String> result = new ArrayList<>();
        for(String other : IDS) {
            if(!other.equals(id)) {
                result.add(other);
            }
        }

        return result;
    }

    private static Transition<String> acquireTransition(String threadId) {
        Map<String, Integer> input = Collections.emptyMap();
        List<String> reset = Collections.emptyList();
        ArrayList<String> inhibitor = new ArrayList<>();

        Map<String, Integer> output = Collections.singletonMap(getCurrentPlace(threadId), 1);

        for(String id : IDS) {
            // Thread cannot acquire lock if any thread already has it
            inhibitor.add(getCurrentPlace(id));
        }


        // Thread cannot acquire lock if it was acquired by the same thread last time
        inhibitor.add(getLastPlace(threadId));

        return new Transition<>(input, reset, inhibitor, output);
    }

    private static Transition<String> releaseTransition(String threadId) {
        Map<String, Integer> input = Collections.singletonMap(getCurrentPlace(threadId), 1);
        Map<String, Integer> output = Collections.singletonMap(getLastPlace(threadId), 1);
        ArrayList<String> reset = new ArrayList<>();
        ArrayList<String> inhibitor = new ArrayList<>();

        for(String id : getOtherIds(threadId)) {
            reset.add(getLastPlace(id));
        }

        return new Transition<>(input, reset, inhibitor, output);
    }

    private static void checkSafety(PetriNet<String> pnet) {
        HashSet<Transition<String>> transitions = new HashSet<>();
        for(String id : IDS) {
            transitions.add(acquireTransition(id));
            transitions.add(releaseTransition(id));
        }

        var reachableStates = pnet.reachable(transitions);

        System.out.println(reachableStates.size());

        for(var state : reachableStates) {
            if(!isStateSafe(state)) {
                System.out.println("State " + state + " is not safe");
            }
        }
    }

    private static boolean isStateSafe(Map<String, Integer> state) {
        int threadsInCriticalSection = 0;
        for(String id : IDS) {
            int isInCriticalSection = state.getOrDefault(getCurrentPlace(id), 0);
            threadsInCriticalSection += isInCriticalSection;

            if(isInCriticalSection > 0) {
                if(state.getOrDefault(getLastPlace(id), 0) > 0) {
                    return false;
                }
            }
        }

        return threadsInCriticalSection <= 1;
    }

}
