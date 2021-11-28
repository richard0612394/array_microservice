
package io.helidon.examples.quickstart.mp;

import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ServiceUnavailableException;


@ApplicationScoped
public class ArrayPermutationProvider {
    //constants specifying range of int values arrayKeys can obtain
    private static final int MAX_ID_VALUE = 999999999;
    private static final int MIN_ID_VALUE = 100000000;
    private static final int THREAD_POOL_SIZE = 1;
    //constant given by maximum value factorial can be computed using long
    public static final int MAXIMUM_ALLOWED_ARRAY_SIZE = 20;

    //storage of arrays passed in by setArray
    private Map<String, List> arraysCache;
    //storage of results
    private Map<String, List<List<Object>>> permutationsCache;
    //storage of progress
    private Map<String, Long> progressCache;
    //storage of result size
    private Map<String, Long> targetResultSizeCache;
    //array keys
    private List<String> arrayKeys;
    private final ExecutorService permutationCalculatingExecutor;
    private int maximumSuccessfullyComputedPermutationsArraySize;

    /**
     * Create a new array permutation provider.
     */
    @Inject
    public ArrayPermutationProvider() {
        this.arraysCache = new ConcurrentHashMap<>();
        this.permutationsCache = new ConcurrentHashMap<>();
        this.progressCache = new ConcurrentHashMap<>();
        this.targetResultSizeCache = new ConcurrentHashMap<>();
        this.permutationCalculatingExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.arrayKeys = Collections.synchronizedList(new ArrayList<>());
        this.maximumSuccessfullyComputedPermutationsArraySize = 0;
    }

    /**
     * Add array to arraysCache if it was not already added.
     * If array does not exist in arraysCache:
     *  Calculate size of array of all permutations of input array and store in targetResultSizeCache.
     *  Put -1 value to progressCache meaning calculation did not yet start,
     *  because calculation can be in progress even if progress is 0.
     *  Add empty array to permutationsCache, where result will be stored.
     *  Add arrayKey to ids array.
     *  Return new array key.
     * If array already exists in arraysCache, return its array key.
     *
     * @param   array    array of objects
     * @return           arrayKey uniquely identifying the array
     */
    public synchronized String setArray(List array) {
        if (!isMemoryAvailable()) {
            invokeGarbageCollector();
            throw new ServiceUnavailableException("Service not available at the moment.");
        }
        if (array.size() > MAXIMUM_ALLOWED_ARRAY_SIZE) {
            throw new InvalidParameterException("Array size is too large, maximum allowed size is "
                    + MAXIMUM_ALLOWED_ARRAY_SIZE + ".");
        }
        String arrayKey = getArrayKeyIfArrayExists(array);
        if (arrayKey != null) {
            return arrayKey;
        } else {
            return submitNewArrayPermutationCalculation(generateRandomId(), array);
        }
    }

    /**
     * Add new array to arraysCache.
     *
     * @param   newArrayKey array of objects
     * @param   array       array of objects
     * @return              arrayKey uniquely identifying the array
     */
    private synchronized String submitNewArrayPermutationCalculation(String newArrayKey, List array) {
        this.arraysCache.put(newArrayKey, array);
        long arrayFactorial = factorial(array.size());
        this.targetResultSizeCache.put(newArrayKey, arrayFactorial);
        this.progressCache.put(newArrayKey, -1L);
        this.permutationsCache.put(newArrayKey, new ArrayList<>());
        arrayKeys.add(newArrayKey);
        permutationCalculatingExecutor.submit(() -> permutationsCache.put(newArrayKey, getPermutationsOfArray(newArrayKey)));
        return newArrayKey;
    }

    /**
     * Returns progress of permutation operation of an array in percents.
     * Array is identified by unique arrayKey.
     *
     * @param   arrayKey    unique key identifying array
     * @return              String value of progress of permutation operation in percents
     */
    public String getProgressInPercents(String arrayKey) {
        if (!arrayKeyExists(arrayKey)) {
            throw new InvalidParameterException("Parameter '" + arrayKey + "' not registered.");
        }
        if (permutationDidNotStart(arrayKey)) {
            return "0%";
        }
        return progressCache.get(arrayKey).toString() + "%";
    }

    /**
     * Returns list of all possible permutations of original array.
     * Array is identified by unique arrayKey.
     * Permutation calculation can be in 3 states:
     *  1. Already done, in this case cached result is returned.
     *  2. Not started yet, in this case, calculation of all permutations starts.
     *  3. In progress, in this case, waits until calculation is done.
     * @param   arrayKey    unique key identifying array
     * @return              List of permutations
     */
    public List<List<Object>> getPermutationsOfArray(String arrayKey) throws InvalidKeyException {
        if (!arrayKeyExists(arrayKey)) {
            invokeGarbageCollector();
            throw new InvalidParameterException("Parameter '" + arrayKey + "' not registered.");
        }
        if (permutationIsDone(arrayKey)) {
            return permutationsCache.get(arrayKey);
        } else if (permutationDidNotStart(arrayKey)) {
            progressCache.put(arrayKey, 0L);
            return getAllPermutationsOfAnArray(arrayKey);
        } else {
            while (!permutationIsDone(arrayKey)) {
                threadSleepInMillis(10);
            }
            return permutationsCache.get(arrayKey);
        }
    }

    /**
     * Get key of an array if it exists in map arraysCache. Otherwise, return null.
     *
     * @param   newArray    array
     * @return              array key or null
     */
    private synchronized String getArrayKeyIfArrayExists(List newArray) {
        return arraysCache.entrySet()
                .stream()
                .filter(input -> newArray.containsAll(arraysCache.get(input.getKey()))
                        && arraysCache.get(input.getKey()).containsAll(newArray))
                .map(input -> input.getKey())
                .findAny()
                .orElse(null);
    }

    /**
     * Generates all possible permutations of array identified by arrayKey.
     * Array is stored in global variable arraysCache.
     * Permutations are stored in global variable permutationsCache.
     *
     * @param   arrayKey    unique key of the array
     * @return              array of lists of permutations
     */
    private List<List<Object>> getAllPermutationsOfAnArray(String arrayKey) {
        List array = arraysCache.get(arrayKey);
        Object[] sourceArrayObjects = array.toArray();
        performPermutationOnSubArray(0, sourceArrayObjects, arrayKey);
        updateProgressOfPermutation(arrayKey);
        if (maximumSuccessfullyComputedPermutationsArraySize < array.size()) {
            maximumSuccessfullyComputedPermutationsArraySize = array.size();
        }
        return permutationsCache.get(arrayKey);
    }

    /**
     * Recursively calculate all permutations of array sourceArrayObjects.
     * Results are stored in permutationsCache map and identified by arrayKey.
     * Check if at least 90% of memory is available on every 100-th recursion
     *
     * @param   i                   index
     * @param   sourceArrayObjects  array whose permutations are being calculated
     * @param   arrayKey            array identifier
     */
    private void performPermutationOnSubArray(int i, Object[] sourceArrayObjects, String arrayKey) {
        if (permutationsCache.get(arrayKey).size() % 100 == 0) {
            if (!isMemoryAvailable()) {
                clearArrayFromCache(arrayKey);
                return;
            }
        }
        updateProgressOfPermutation(arrayKey);
        int sizeOfSourceArray = sourceArrayObjects.length;
        if (i == sizeOfSourceArray - 1) {
            List<Object> list = new ArrayList<>();
            for(int j = 0; j < sizeOfSourceArray; j++) {
                list.add(sourceArrayObjects[j]);
            }
            permutationsCache.get(arrayKey).add(list);
        } else {
            for (int j = i, l = sizeOfSourceArray; j < l; j++) {
                Object temp = sourceArrayObjects[j];
                sourceArrayObjects[j] = sourceArrayObjects[i];
                sourceArrayObjects[i] = temp;
                performPermutationOnSubArray(i + 1, sourceArrayObjects, arrayKey);
                temp = sourceArrayObjects[j];
                sourceArrayObjects[j] = sourceArrayObjects[i];
                sourceArrayObjects[i] = temp;
            }
        }
    }

    /**
     * Updates progress of permutation calculation specified by arrayKey
     *
     * @param   arrayKey    unique key of the array
     */
    private void updateProgressOfPermutation(String arrayKey) {
        progressCache.put(arrayKey,
                100 * (permutationsCache.get(arrayKey).size() / targetResultSizeCache.get(arrayKey)));
    }

    /**
     * Returns factorial of a number.
     * Argument must be positive integer value.
     *
     * @param   n   integer value for factorial to be calculated from
     * @return      calculated factorial value
     */
    private long factorial(int n) {
        if (n < 0) {
            throw new InvalidParameterException("Can not calculate factorial from negative number.");
        }
        if (n == 0) {
            return 1;
        } else {
            return n * factorial(n - 1);
        }
    }

    /**
     * Returns random string composed of digits.
     *
     * @return      random string
     */
    private String generateRandomId() {
        int randomNumber = new Random().nextInt((MAX_ID_VALUE - MIN_ID_VALUE) + 1) + MIN_ID_VALUE;
        return String.valueOf(randomNumber);
    }

    /**
     * Returns true if arrayKey already exists in arrayKeys list,
     * otherwise returns false.
     *
     * @param   arrayKey  String identifier value
     * @return      boolean value
     */
    private boolean arrayKeyExists(String arrayKey) {
        return arrayKeys.contains(arrayKey);
    }

    /**
     * Returns true if calculation of all permutations of an array identified by arrayKey is already done,
     * otherwise returns false.
     * Method throws InvalidKeyException in case arrayKey is null. Such situation can happen when permutation
     * calculation failed and array is being removed while other client waits for its result.
     *
     * @param   arrayKey    array identifier value
     * @return              boolean value
     */
    private boolean permutationIsDone(String arrayKey) throws InvalidKeyException {
        try {
            return progressCache.get(arrayKey) == 100;
        } catch (NullPointerException e) {
            throw new InvalidKeyException("Invalid array key");
        }
    }

    /**
     * Returns true if calculation of all permutations of an array identified by arrayKey did not start yet,
     * otherwise returns false.
     *
     * @param   arrayKey    array identifier value
     * @return              boolean value
     */
    private boolean permutationDidNotStart(String arrayKey) {
        return progressCache.get(arrayKey) == -1;
    }

    /**
     * Sleeps amount of milliseconds given by parameter
     *
     * @param   millis    amount of milliseconds to sleep
     */
    private void threadSleepInMillis(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Remove data identified by arrayKey from every cache.
     * Trigger garbage collector so that memory is freed up.
     *
     * @param   arrayKey    array identifier value
     */
    private void clearArrayFromCache(String arrayKey) {
        arraysCache.remove(arrayKey);
        permutationsCache.remove(arrayKey);
        targetResultSizeCache.remove(arrayKey);
        progressCache.remove(arrayKey);
        arrayKeys.remove(arrayKey);
        invokeGarbageCollector();
    }

    /**
     * Get maximum available runtime memory.
     *
     * @return   long value of memory
     */
    private long getMaxAvailableMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    /**
     * Get free runtime memory.
     *
     * @return   long value of memory
     */
    private long getFreeRuntimeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * Get total used runtime memory.
     *
     * @return   long value of memory
     */
    private long getTotalUsedRuntimeMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Invoke garbage collector.
     *
     */
    private void invokeGarbageCollector() {
        Runtime.getRuntime().gc();
    }

    /**
     * Return true if at least 10% of runtime memory is available.
     *
     * @return true if more than 10% of memory is available
     */
    private boolean isMemoryAvailable() {
        return getTotalUsedRuntimeMemory() / getMaxAvailableMemory() < 0.9;
    }
}
