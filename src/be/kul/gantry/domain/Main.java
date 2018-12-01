package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, ParseException {

        String inputFileName = args[0];
        String outputFileName = args[1];

        //Stellen een mogelijke oplossing op
        // Lezen de json file in en bouwen de kade op

        Solution solution = new Solution(inputFileName);

        //Afhandelen van de inputjobs
        solution.handleInputJobs();

        //Afhandelen van de outputjobs
        solution.handleOutputJobs();

        //Wegschrijven naar de outputfile
        solution.writeOutput(outputFileName);

    }
}
