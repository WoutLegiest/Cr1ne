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
        if(Solution.isCrossed())
            solution.handleInputJobsCrossed();
        else{
            System.out.println("Begin aan de input");
            solution.handleInputJobsStacked();
        }


        System.out.println("Beginnen aan output");
        //Afhandelen van de outputjobs
        solution.handleOutputJobs();

        System.out.println("Beginnen aan het uitschrijven");
        //Wegschrijven naar de outputfile
        solution.writeOutput(outputFileName);

    }
}
