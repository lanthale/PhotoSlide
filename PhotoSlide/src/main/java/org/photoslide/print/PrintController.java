/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.print;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.fxml.Initializable;
import javafx.scene.control.DialogPane;
import org.photoslide.ThreadFactoryPS;

/**
 *
 * @author selfemp
 */
public class PrintController implements Initializable {
    
    private ExecutorService executor;
    private ExecutorService executorParallel;    
    private DialogPane dialogPane;

    @Override
    public void initialize(URL url, ResourceBundle rb) {        
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("SearchToolExecutor"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("SearchToolExecutor"));        
    }

    public void shutdown() {
        executor.shutdown();
        executorParallel.shutdown();
    }

    public void setDialogPane(DialogPane pane) {
        this.dialogPane = pane;        
    }

    

}
