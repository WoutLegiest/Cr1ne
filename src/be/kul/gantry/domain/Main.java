package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, ParseException {

        String inputFileName = "./1_10_100_4_FALSE_65_50_50.json";
        String outputFileName = "output.csv";

        /*Stellen een mogelijke oplossing op
          Lezen de json file in en bouwen de kade op
        */
        Solution solution = new Solution(inputFileName);

        //Afhandelen van de inputjobs
        solution.handleInputJobs();

        //Afhandelen van de outputjobs
        solution.handleOutputJobs();

        //Wegschrijven naar de outputfile
        solution.writeOutput(outputFileName);

    }
}
