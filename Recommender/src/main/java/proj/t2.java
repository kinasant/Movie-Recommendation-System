package proj;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import me.xdrop.fuzzywuzzy.FuzzySearch;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class t2 {

    private static final int HASH_FUNCTIONS = 10; 
    private static final int BANDS = 5; 
    private static final double SIMILARITY_THRESHOLD = 0.7; 

    public static void main(String[] args) {

        Map<String, List<Object>> dfRatings = loadCsvData("ratings.csv"); 
        
        Map<Integer, String> movieTitles = loadMovieTitles("movies.csv"); 
        Map<String, Integer> movieIds = reverseMovieTitles(movieTitles); 

        
        Map<Integer, Map<Integer, Float>> userItemMatrix = createUserItemMatrix(dfRatings);

        
        Map<Integer, List<Integer>> userSignatures = generateLSHSignatures(userItemMatrix);

        Scanner scanner = new Scanner(System.in);

        
        while (true) {
            System.out.print("Enter a movie title (or 'quit' to exit): ");
            String movieTitleInput = scanner.nextLine();

            if (movieTitleInput.equalsIgnoreCase("quit")) {
                break; 
            }

            
            Integer movieId = FuzzyMatcher.fuzzyMatching(movieIds, movieTitleInput, true);
            if (movieId == null) {
                System.out.println("Movie not found in the database.");
                continue; 
            }

            
            Set<Integer> usersWhoLikedMovie = findUsersWhoLikedMovie(movieId, userItemMatrix);

            
            List<Integer> recommendations = generateRecommendationsFromUsers(
                    usersWhoLikedMovie, userItemMatrix, movieTitles, movieId);

           
            System.out.println("Movie recommendations based on '" + movieTitleInput + "':");
            for (int recommendedMovieId : recommendations) {
                System.out.println("- " + movieTitles.get(recommendedMovieId));
            }
        }

        scanner.close();
    }

    
    private static Map<String, List<Object>> loadCsvData(String filename) {
        Map<String, List<Object>> data = new HashMap<>();
        data.put("userId", new ArrayList<>());
        data.put("movieId", new ArrayList<>());
        data.put("rating", new ArrayList<>());

        try (CSVReader reader = new CSVReader(new FileReader(filename))) {
            reader.readNext(); // Skip header

            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                ((List<Integer>) (List<?>) data.get("userId")).add(Integer.parseInt(nextLine[0]));
                ((List<Integer>) (List<?>) data.get("movieId")).add(Integer.parseInt(nextLine[1]));
                ((List<Float>) (List<?>) data.get("rating")).add(Float.parseFloat(nextLine[2]));
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }
        return data;
    }

    
    private static Map<Integer, String> loadMovieTitles(String filename) {
        Map<Integer, String> movieTitles = new HashMap<>();
        try (CSVReader reader = new CSVReader(new FileReader(filename))) {
            reader.readNext();

            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                movieTitles.put(Integer.parseInt(nextLine[0]), nextLine[1]);
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }
        return movieTitles;
    }

    
    private static Map<Integer, Map<Integer, Float>> createUserItemMatrix(Map<String, List<Object>> dfRatings) {
        Map<Integer, Map<Integer, Float>> userItemMatrix = new HashMap<>();
        List<Integer> userIds = (List<Integer>) (List<?>) dfRatings.get("userId");
        List<Integer> movieIds = (List<Integer>) (List<?>) dfRatings.get("movieId");
        List<Float> ratings = (List<Float>) (List<?>) dfRatings.get("rating");

        for (int i = 0; i < userIds.size(); i++) {
            int userId = userIds.get(i);
            int movieId = movieIds.get(i);
            float rating = ratings.get(i);

            userItemMatrix.putIfAbsent(userId, new HashMap<>());
            userItemMatrix.get(userId).put(movieId, rating);
        }
        return userItemMatrix;
    }

    
    private static Map<Integer, List<Integer>> generateLSHSignatures(Map<Integer, Map<Integer, Float>> userItemMatrix) {
        Map<Integer, List<Integer>> userSignatures = new HashMap<>();
        Random random = new Random(5);

        for (int user : userItemMatrix.keySet()) {
            List<Integer> signature = new ArrayList<>(Collections.nCopies(HASH_FUNCTIONS, Integer.MAX_VALUE));
            for (int h = 0; h < HASH_FUNCTIONS; h++) {
                for (int movie : userItemMatrix.get(user).keySet()) {
                    int hashValue = (int) ((random.nextDouble() * movie + random.nextDouble()) % Integer.MAX_VALUE);
                    signature.set(h, Math.min(signature.get(h), hashValue));
                }
            }
            userSignatures.put(user, signature);
        }
        return userSignatures;
    }

    
    private static Map<String, Integer> reverseMovieTitles(Map<Integer, String> movieTitles) {
        Map<String, Integer> movieIds = new HashMap<>();
        for (Map.Entry<Integer, String> entry : movieTitles.entrySet()) {
            movieIds.put(entry.getValue(), entry.getKey());
        }
        return movieIds;
    }

    
    private static Set<Integer> findUsersWhoLikedMovie(int movieId,
                                                      Map<Integer, Map<Integer, Float>> userItemMatrix) {
        Set<Integer> usersWhoLikedMovie = new HashSet<>();
        for (int user : userItemMatrix.keySet()) {
            if (userItemMatrix.get(user).containsKey(movieId) &&
                userItemMatrix.get(user).get(movieId) >= 4.0) { 
                usersWhoLikedMovie.add(user);
            }
        }
        return usersWhoLikedMovie;
    }

    
    private static List<Integer> generateRecommendationsFromUsers(Set<Integer> users,
                                                                  Map<Integer, Map<Integer, Float>> userItemMatrix,
                                                                  Map<Integer, String> movieTitles,
                                                                  int inputMovieId) {

        Map<Integer, Double> movieScores = new HashMap<>();
        for (int user : users) {
            for (int movie : userItemMatrix.get(user).keySet()) {
                if (movie != inputMovieId) { 
                    double score = userItemMatrix.get(user).get(movie);
                    movieScores.put(movie, movieScores.getOrDefault(movie, 0.0) + score);
                }
            }
        }

        
        List<Map.Entry<Integer, Double>> sortedScores = new ArrayList<>(movieScores.entrySet());
        sortedScores.sort(Map.Entry.<Integer, Double>comparingByValue().reversed());

        List<Integer> recommendations = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Integer, Double> entry : sortedScores) {
            if (count >= 10) { 
                break;
            }
            recommendations.add(entry.getKey());
            count++;
        }

        return recommendations;
    }
}


class FuzzyMatcher {

    public static Integer fuzzyMatching(Map<String, Integer> mapper, String favMovie, boolean verbose) {

        List<Tuple> matchTuple = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : mapper.entrySet()) {
            String title = entry.getKey();
            Integer idx = entry.getValue();
            int ratio = FuzzySearch.ratio(title.toLowerCase(), favMovie.toLowerCase());
            if (ratio >= 60) {
                matchTuple.add(new Tuple(title, idx, ratio));
            }
        }

        matchTuple.sort(Comparator.comparingInt((Tuple t) -> t.ratio).reversed());

        if (matchTuple.isEmpty()) {
            System.out.println("Oops! No match is found");
            return null;
        }

        if (verbose) {
            List<String> matchedTitles = new ArrayList<>();
            for (Tuple tuple : matchTuple) {
                matchedTitles.add(tuple.title);
            }
            System.out.println("Found possible matches in our database: " + matchedTitles);
        }

        return matchTuple.get(0).idx;
    }

    
    private static class Tuple {
        String title;
        Integer idx;
        int ratio;

        public Tuple(String title, Integer idx, int ratio) {
            this.title = title;
            this.idx = idx;
            this.ratio = ratio;
        }
    }
}