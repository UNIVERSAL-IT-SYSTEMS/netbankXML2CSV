/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package appmain;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;

/**
 *
 * @author pekardy.milan
 */
public class AppMain {
    
    private static final String DEFAULT_IMPORT_FOLDER = "import";
    private static final String DEFAULT_EXPORT_FOLDER = "export";
    
    private final SAXBuilder jdomBuilder;
    
    private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    
    public static void main(String[] args){
        
        try{
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());             
            UIManager.put("OptionPane.yesButtonText", "Igen");
            UIManager.put("OptionPane.noButtonText", "Nem");
        }catch(Exception ex){
            ex.printStackTrace();
        }
        
        try{
            
            AppMain app = new AppMain();
            app.init();
            
            File importFolder = new File(DEFAULT_IMPORT_FOLDER);
            if(!importFolder.isDirectory() || !importFolder.exists()){
                JOptionPane.showMessageDialog(null, "Az IMPORT mappa nem elérhető!\n"
                        + "Ellenőrizze az elérési utat és a jogosultságokat!\n"
                        + "Mappa: " + importFolder.getAbsolutePath(), "Információ", 
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            File exportFolder = new File(DEFAULT_EXPORT_FOLDER);
            if(!exportFolder.isDirectory() || !exportFolder.exists()){
                JOptionPane.showMessageDialog(null, "Az EXPORT mappa nem elérhető!\n"
                        + "Ellenőrizze az elérési utat és a jogosultságokat!\n"
                        + "Mappa: " + exportFolder.getAbsolutePath(), "Információ", 
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            List<File> xmlFiles = app.getXMLFiles(importFolder);
            if(xmlFiles == null || xmlFiles.isEmpty()){
                JOptionPane.showMessageDialog(null, "Nincs beolvasandó XML fájl!\n"
                        + "Mappa: " + importFolder.getAbsolutePath(), "Információ", 
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            StringBuilder fileList = new StringBuilder();
            xmlFiles.stream().forEach(xml -> 
                fileList.append("\n").append(xml.getName())
            );
            int ret = JOptionPane.showConfirmDialog(null, 
                    "Beolvasásra előkészített fájlok:\n" + fileList + "\n\nIndulhat a feldolgozás?", 
                    "Megtalált XML fájlok", 
                    JOptionPane.YES_NO_OPTION, 
                    JOptionPane.QUESTION_MESSAGE);
            
            if(ret == JOptionPane.OK_OPTION){
                String csvName = "tranzakcio_lista_" + df.format(new Date()) + "_" + System.currentTimeMillis() + ".csv";
                File csv = new File(DEFAULT_EXPORT_FOLDER + "/" + csvName);
                app.writeCSV(csv, Arrays.asList(app.getHeaderLine()));
                xmlFiles.stream().forEach(xml -> {
                    List<String> lines = app.readXMLData(xml);
                    if(lines != null)
                        app.writeCSV(csv, lines);
                });
                if(csv.isFile() && csv.exists()){
                    JOptionPane.showMessageDialog(null, "A CSV fájl sikeresen előállt!\n"
                            + "Fájl: " + csv.getAbsolutePath()
                        , "Információ", 
                        JOptionPane.INFORMATION_MESSAGE);
                    app.openFile(csv);
                }
            }
            else{
                JOptionPane.showMessageDialog(null, "Feldolgozás megszakítva!"
                    , "Információ", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
            
            
        }
        catch(Exception ex){
            JOptionPane.showMessageDialog(null, "Nem kezelt kivétel!\n"
                    + ExceptionUtils.getStackTrace(ex), 
                    "Hiba", JOptionPane.ERROR_MESSAGE);
        }
        
    }
    
    private final List<Field> fields;
    
    public AppMain() {
        
        fields = new ArrayList<>();
        
        jdomBuilder = new SAXBuilder();       
                
    }
    
    private void openFile(File file){
        try{
            if(Desktop.isDesktopSupported()){
                Desktop.getDesktop().open(file);
            }   
        }
        catch(IOException ioe){
            
        }
    }
    
    private void writeCSV(File csv, List<String> content){
        BufferedWriter writer = null;
        try{
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csv, true), "ISO-8859-2"));
            for(String line : content){
                writer.write(line + System.lineSeparator());
            }            
            writer.flush();
        }
        catch(IOException | HeadlessException e){
            JOptionPane.showMessageDialog(null, "Hiba a CSV fájl írása közben!\n"
                                                + ExceptionUtils.getStackTrace(e), 
                                                "Hiba", JOptionPane.ERROR_MESSAGE);
        }
        finally{
            if(writer != null){
                try {
                    writer.close();
                } catch (IOException ex) {ex.printStackTrace();}
            }        
        }
    }    
    
    private String getHeaderLine(){
        StringBuilder result = new StringBuilder();
        fields.stream().forEach(field -> result.append(";").append(field.getHeaderName()));
        return result.substring(1);
    }
    
    private List<String> readXMLData(File xml){        
        try {            
            List<String> transactions = new ArrayList<>();
            
            Document xmlDoc = jdomBuilder.build(xml);
            Element report = xmlDoc.getRootElement();
         
            ElementFilter filter = new ElementFilter("G_TR");
            Iterator<Element> c = report.getDescendants(filter);
            while(c.hasNext()){
                Element e = c.next();
                transactions.add(processTransactionElement(e));
            }
            return transactions;
        }catch(JDOMException | IOException ex) {
            JOptionPane.showMessageDialog(null, "Hiba az XML fájl feldolgozása közben!\n"
                                                + ExceptionUtils.getStackTrace(ex)
                                                + "Fájl: " + xml.getAbsolutePath(), 
                                                "Hiba", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }
    
    private String processTransactionElement(Element e){
        StringBuilder result = new StringBuilder();
        fields.stream().forEach(field ->
            result.append(";").append(field.getConverter().convertElement(e.getChild(field.getTagName())))
        );
        return result.substring(1);
    }
    
    private List<File> getXMLFiles(File folder){
        File[] xmlFiles = folder.listFiles((File pathname) -> {
            return pathname.isFile() && !pathname.isHidden() && 
                   (pathname.getName().endsWith(".xml") || 
                    pathname.getName().endsWith(".XML"));
        });
        return Arrays.asList(xmlFiles);
    }
    
    private void init(){
        try{
            ElementConverter defaultConverter = e -> e.getText();
        
            ElementConverter partnerConverter = e -> {
                String text = e.getText();
                String[] partnerTypes = {"Fizető:", "Kedvezményezett:", "Bonyolító:"};
                for(String partnerType : partnerTypes){
                    if(text.contains(partnerType)){
                        int idx = text.indexOf(partnerType);
                        String partner = text.substring(idx + partnerType.length(), text.indexOf(",")).trim();
                        return partner;
                    }
                }            
                return "";
            };

            ElementConverter commentConverter = e -> {
                String text = e.getText();
                String commentString = "Közlemény:";
                if(text.contains(commentString)){
                    int idx = text.indexOf(commentString);
                    String comment = text.substring(idx + commentString.length());
                    return comment;
                }            
                return "";
            };
            
            ElementConverter accNumConverter = e -> {
                String text = e.getText();
                String[] partnerTypes = {"Fizető:", "Kedvezményezett:", "Bonyolító:"};
                for(String partnerType : partnerTypes){
                    if(text.contains(partnerType)){
                        int idx = text.indexOf(",");
                        String tmp = text.substring(idx + 1).trim();
                        StringBuilder accNum = new StringBuilder();
                        for(char ch : tmp.toCharArray()){
                            if(Character.isDigit(ch) || ch == '-'){
                                accNum.append(ch);
                            }
                            else{
                                break;
                            }
                        };
                        if(!accNum.toString().contains("-")){
                            return "";
                        }
                        return accNum.toString().trim();
                    }
                }            
                return "";
            };
            
            fields.add(new Field("Tranzakció azonosító", "F_TRCODE", defaultConverter));
            fields.add(new Field("Értéknap", "F_DAT1", e -> e.getAttributeValue("fv")));
            fields.add(new Field("Összeg", "F_TRAMOUNT", defaultConverter));
            fields.add(new Field("Devizanem", "F_CUR", defaultConverter));
            fields.add(new Field("Partner", "F_LINES", partnerConverter));
            fields.add(new Field("Közlemény", "F_LINES", commentConverter));
            fields.add(new Field("Tranzakció neve", "F_NAME", defaultConverter));
            fields.add(new Field("Ellenszámla", "F_LINES", accNumConverter));
        }
        catch(Exception ex){
            JOptionPane.showMessageDialog(null, "Hiba az inicializálás közben!\n"
                + ExceptionUtils.getStackTrace(ex), "Hiba", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private class Field {
        
        private final String headerName;
        private final String tagName;
        private final ElementConverter converter;
        
        public Field(String headerName, String tagName, ElementConverter converter){
            this.headerName = headerName;
            this.tagName = tagName;
            this.converter = converter;
        }

        public String getHeaderName() {
            return headerName;
        }

        public String getTagName() {
            return tagName;
        }

        public ElementConverter getConverter() {
            return converter;
        }
        
    }
    
    private interface ElementConverter {
        
        public abstract String convertElement(Element element);
        
    }
    
}
