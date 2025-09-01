package org.example;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class ExcelColumnReader {

    public static void main(String[] args) {
        try {
            // Specify the path to your Excel file
            String excelFilePath = "src/main/resources/NiftyStocks.xlsx";

            // Specify the column index you want to read (0-based index)
            int columnIndexToRead = 2; // Example: Read the third column (index 2)

            // Read the specified column
            readExcelColumn(excelFilePath, columnIndexToRead);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<String> niftyStocks = new ArrayList<>();

    public static ArrayList<String> readExcelColumn(String filePath, int columnIndex) throws IOException {
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            // Assuming you are reading from the first sheet (index 0)
            Sheet sheet = workbook.getSheetAt(0);

            // Iterate through rows
            for (Row row : sheet) {
                // Get the cell in the specified column for each row
                Cell cell = row.getCell(columnIndex);

                // Check if the cell is not null
                if (cell != null) {
                    // Get the cell value (you may need to handle different cell types)
                    String cellValue = cell.getStringCellValue();
                    niftyStocks.add(cellValue);
                    //System.out.println("Cell Value: " + cellValue);
                }
            }

            return niftyStocks;
        }
    }
}
