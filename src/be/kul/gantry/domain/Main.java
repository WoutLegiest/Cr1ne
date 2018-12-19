package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, ParseException {

        String inputFileName = "2_10_100_4_TRUE_65_50_50.json"; //= args[0];
        String outputFileName ="output.csv"; // args[1];

        //Stellen een mogelijke oplossing op
        // Lezen de json file in en bouwen de kade op

        Solution solution = new Solution(inputFileName);

        solution.executeSolution();

        System.out.println("Beginnen aan het uitschrijven");
        //Wegschrijven naar de outputfile
        solution.writeOutput(outputFileName);

    }
}
