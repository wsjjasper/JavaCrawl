package shujiaw;

import java.io.File;
import java.io.IOException;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.Label;

public class MainApp extends Application {

    private Stage primaryStage;
    private BorderPane rootLayout;
    @FXML
    private Label wenJuanXinFileName;
    private File wenJuanXinFile;
    @FXML
    private Label assessmentFileName;
    private File assessmentFile;
    @FXML
    private Label applicationFileName;
    private File applicationFile;
    @FXML
    private Label updatedFileName;
    private File updatedFile;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("转换工具");

        initRootLayout();

        showConvertOverview();
    }

    /**
     * Initializes the root layout.
     */
    public void initRootLayout() {
        try {
            // Load root layout from fxml file.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("view/RootLayout.fxml"));
            rootLayout = (BorderPane) loader.load();

            // Show the scene containing the root layout.
            Scene scene = new Scene(rootLayout);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows the convert overview inside the root layout.
     */
    public void showConvertOverview() {
        try {
            // Load convert overview.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("view/ConvertOverview.fxml"));
            AnchorPane convertOverview = (AnchorPane) loader.load();

            // Set convert overview into the center of root layout.
            rootLayout.setCenter(convertOverview);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the main stage.
     *
     * @return
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }

    private File selectExcelFileButtonAction(Label label){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xls", "*.xlsx", "*.csv"));
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if(selectedFile != null){
            label.setText(selectedFile.getName());
        }else{
            label.setText("未选择");
        }
        return selectedFile;
    }

    @FXML
    protected void selectWenJuanXinButtonAction(ActionEvent event) {
        wenJuanXinFile = selectExcelFileButtonAction(wenJuanXinFileName);
    }

    @FXML
    protected void selectApplicationButtonAction(ActionEvent event) {
        applicationFile = selectExcelFileButtonAction(applicationFileName);
    }

    @FXML
    protected void selectAssessmentButtonAction(ActionEvent event) {
        assessmentFile = selectExcelFileButtonAction(assessmentFileName);
    }

    @FXML
    protected void selectUpdatedFileButtonAction(ActionEvent event) {
        updatedFile = selectExcelFileButtonAction(updatedFileName);
    }

    public void startConversionButtonAction(ActionEvent actionEvent) {
        Alert alert;
        if (wenJuanXinFileName.getText().contains("未选择") || assessmentFileName.getText().contains("未选择") || applicationFileName.getText().contains("未选择")) {
            alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText("请检查");
            alert.setContentText("必须先选择问卷星，总表和评估人员表！");
        }else{
            try{
                CsvConverter converter = new CsvConverter();
                String fileName = converter.startConversion(wenJuanXinFile.getPath(), applicationFile.getPath(), assessmentFile.getPath());
                alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("成功");
                alert.setHeaderText("文件转换成功！");
                alert.setContentText(fileName);
            }catch(Exception e){
                alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("系统错误");
                alert.setHeaderText("请检查文件是否选错");
                alert.setContentText(e.getMessage());
            }
        }
        alert.show();
    }

    public void startGenerationButtonAction(ActionEvent actionEvent) {
        Alert alert;
        if (updatedFileName.getText().contains("未选择")) {
            alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText("请检查");
            alert.setContentText("必须先选择修订后的文件！");
        }else{
            alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("成功");
            alert.setHeaderText("文件生成成功！");
            alert.setContentText("[生成后的文件.xml]");
        }
        alert.show();
    }
}
