import java.util.*;
import java.io.*;


class MainClass {

    static int NUMBER_OF_USERS = 26;
    static int NUMBER_OF_DISKS = 2;
    static int NUMBER_OF_PRINTERS = 3;
    
    static Thread user[] = new Thread[NUMBER_OF_USERS];
    static Disk disk[] = new Disk[NUMBER_OF_DISKS];
    static Printer printer[] = new Printer[NUMBER_OF_PRINTERS];


    //Main
    public static void main(String[] args){

    	ResourceManager diskManager = new ResourceManager(NUMBER_OF_DISKS);
    	ResourceManager printerManager = new ResourceManager(NUMBER_OF_PRINTERS);
    	DirectoryManager directoryManager = new DirectoryManager();


	//NUMBER_OF_USERS = Integer.parseInt(args[0].substring(1));
	//NUMBER_OF_DISKS = Integer.parseInt(args[1].substring(1));
	//NUMBER_OF_PRINTERS = Integer.parseInt(args[2].substring(1));


        for (int i = 0; i < NUMBER_OF_DISKS; i++){
            disk[i] = new Disk();
        }

        for (int i = 0; i < NUMBER_OF_PRINTERS; i++){
            printer[i] = new Printer();
        }

        for (int i = 0; i < NUMBER_OF_USERS; i++) { 
            user[i] = new Thread(new UserThread("USER" + i, disk, diskManager, directoryManager, printer, printerManager));
            user[i].start();
        } 

    }


}


class Disk extends Thread{
    static final int NUM_SECTORS = 2048;
    static final int DISK_DELAY = 80;
    StringBuffer sectors[] = new StringBuffer[NUM_SECTORS];
    int size = 0;
    
    void write(int sector, StringBuffer data){
	sectors[sector] = new StringBuffer(data);
    } 

    void read(int sector, StringBuffer data){
        data.setLength(0);
        data.append(sectors[sector]);
    } 
}



class FileInfo{
    int diskNumber;
    int startingSector;
    int fileLength;

    FileInfo(int disk, int start, int length){
        diskNumber = disk;
        startingSector = start;
        fileLength = length;
    }
}


class DirectoryManager {
    Hashtable<String, FileInfo> T = new Hashtable<String, FileInfo>();

    void enter(StringBuffer key, FileInfo file){
        T.put(key.toString(), file);
    }

    FileInfo lookup(StringBuffer key){
        String k = key.toString();
        if (T.containsKey(k)){
            return T.get(k);
        }
        else {
            return null;
        }
    }
}


class PrintJobThread implements Runnable{

    String filename;
    FileInfo fifo;
    ResourceManager printerManager;
    Printer[] printer;
    Disk[] disk;

    PrintJobThread(FileInfo fi, String file, ResourceManager pm, Printer[] p, Disk[] dk){
        filename = file;
        fifo = fi;
        printerManager = pm;
        printer = p;
        disk = dk;
    }

    public void run(){
        try {
            int p = printerManager.request();
            try {
                BufferedWriter wr = new BufferedWriter(new FileWriter("PRINTER" + (p), true));

                for (int i = 0; i < fifo.fileLength; i++){
                    StringBuffer data = new StringBuffer();
                    disk[fifo.diskNumber].read(fifo.startingSector+i, data);
                    printer[p].print(data, wr);
                }

                System.out.println(filename + " printed to PRINTER" + (p));
                wr.close();
            } catch (IOException e){
                System.err.println(e);
            }
            printerManager.release(p);
        } catch (InterruptedException e){
            System.err.println(e);
        } catch (Exception e){
            System.err.println(e);
        }
    }
}

class Printer {
    
    static final int PRINT_DELAY = 275;
    

    void print(StringBuffer data, BufferedWriter writer){
        try {
	    Thread.sleep(PRINT_DELAY);

            writer.append(data.toString());
            writer.newLine();
        } catch (IOException e){
            System.err.println(e);
        } catch (Exception e) {
            System.err.println(e);
        }
    }
}


class ResourceManager {
    boolean isFree[];
    
    ResourceManager(int numberOfItems){
        isFree = new boolean[numberOfItems];
        for (int i=0; i<isFree.length; ++i)
            isFree[i] = true;
    }

    synchronized int request() throws InterruptedException{
        while (true) {
            for (int i = 0; i < isFree.length; ++i){
                if (isFree[i]){
                    isFree[i] = false;
                    return i;
                }
            }
            this.wait(); // block until someone releases a Resource
        }
    }

    synchronized void release(int index){
        isFree[index] = true;
        this.notify(); // let a waiting thread run
    }
}


class UserThread implements Runnable{
    String user;
    ResourceManager diskManager;
    DirectoryManager directoryManager;
    Disk[] disk;
    Printer[] printer;
    ResourceManager printerManager;

    UserThread(String u, Disk[] dk, ResourceManager dm, DirectoryManager di, Printer[] p, ResourceManager pm){
        user = u;
        diskManager = dm;
        directoryManager = di;
        disk = dk;
        printer = p;
        printerManager = pm;
    }

    public void run(){
        
        try {
            System.out.println("Starting UserThread " + user);
            FileInputStream fstream = new FileInputStream(user); 
            BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

            int fileLength = 0, startingSector = 0, diskNumber = 0;
            String s = "", filename = "";
            StringBuffer strLine = new StringBuffer();

            while ((s = br.readLine()) != null){
                strLine.append(s);

                if (strLine.indexOf(".save") == 0){
                    filename = strLine.substring(6);
                    fileLength = 0;
                    
                    // request disk (need to know start sector in disk)
                    diskNumber = diskManager.request();
                    startingSector = disk[diskNumber].size;
                }
                else if (strLine.indexOf(".end") == 0){
                    // update size
                    disk[diskNumber].size = startingSector + fileLength;

                    // add entry to directorymanager
                    FileInfo fifo = new FileInfo(diskNumber, startingSector, fileLength);
                    directoryManager.enter(new StringBuffer(filename), fifo);

                    System.out.println(filename + " saved to Disk " + diskNumber);
                    // release disk
                    diskManager.release(diskNumber);
                }
                else if (strLine.indexOf(".print") == 0){
                    // lookup information
                    filename = strLine.substring(7);  
                    FileInfo fifo = directoryManager.lookup(new StringBuffer(filename));

                    // sent to print function
                    Thread object = new Thread(new PrintJobThread(fifo, filename, printerManager, printer, disk)); 
                    object.start(); 
                }
                else{
                    // write to system
		    Thread.sleep(80);
                    disk[diskNumber].write(startingSector+fileLength, strLine);
                    fileLength++;
                }
                //System.out.println(strLine.toString());
                strLine.setLength(0);
            }    
            fstream.close();
        } catch (FileNotFoundException e){
            System.err.println(e);
        } catch (Exception e) { 
            System.out.println (e); 
        } 
    } 

}
