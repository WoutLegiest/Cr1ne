package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, ParseException {

        String inputFileName = args[0];
        String outputFileName = args[1];

        //Stellen een mogelijke oplossing op
        // Lezen de json file in en bouwen de kade op


        String[] inputSplit = inputFileName.split("_");

        if(inputSplit[0].equals("1")){
            Solution_One solution = new Solution_One(inputFileName);

            //Afhandelen van de inputjobs
            if(Solution_One.isCrossed())
                solution.handleInputJobsCrossed();
            else
                solution.handleInputJobsStacked();


            //Afhandelen van de outputjobs
            solution.handleOutputJobs();

            System.out.println("Beginnen aan het uitschrijven");
            //Wegschrijven naar de outputfile
            solution.writeOutput(outputFileName);
        }

        else if(inputSplit[0].equals("2")){
            Solution_Double solutionDouble = new Solution_Double(inputFileName);

            solutionDouble.executeSolution();

            System.out.println("Beginnen aan het uitschrijven");
            //Wegschrijven naar de outputfile
            solutionDouble.writeOutput(outputFileName);
        }



    }
}
