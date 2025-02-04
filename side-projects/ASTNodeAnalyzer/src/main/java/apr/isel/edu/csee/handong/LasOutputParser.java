package apr.isel.edu.csee.handong;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;

import java.util.*;



public class LasOutputParser{

    public List<String> lines = new ArrayList<String>();

    public HashMap<String,Integer> nodeCountMap = new HashMap<String, Integer>(103);
    public HashMap<String,Set<String>> parentCollectionMap = new HashMap<String,Set<String>>(103);
    public List<String> changes = new ArrayList<String>();
    
    LasOutputParser(String lasOutputPath){ //modify this to read actual LAS_output.csv

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(lasOutputPath));
            String line = reader.readLine();
            while (line != null) {
                lines.add(line) ;
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    LasOutputParser(List<String> lasResult){ // when a pure las results of a single patch is given
        this.lines = lasResult;
    }



    public void parseNodes(){
        String type = "";
        String node = "";
        String location = new String();
        String buffer = new String();
        String[] buf ;

        for(String line: this.lines){
            buf = line.split("\t") ;
            if(buf.length == 1) break; // end of LAS result
            type = buf[0] ;
            if(type.equals("move")) continue ;

            buffer = line.split("\t")[1];
            node = buffer.split(",")[0].split("\\|")[0].split("\\(")[0] ;
            location = buffer.split(",")[1].split("\\|")[0].split("\\(")[0] ;

            Integer nodeCount = nodeCountMap.get(location);
            if(nodeCount == null){
                nodeCountMap.put(location, new Integer(1));
            }
            else{
                nodeCountMap.put(location, new Integer(nodeCount.intValue()+1));
            }

            if(type.compareTo("delete") == 0 ){
                nodeCount = nodeCountMap.get(node);
                if(nodeCount == null){
                    nodeCountMap.put(node, new Integer(1));
                }
                else{
                    nodeCountMap.put(node, new Integer(nodeCount.intValue()+1));
                }

                Set<String> parentSet = parentCollectionMap.get(node);
                if(parentSet == null){
                    parentSet = new HashSet<>();
                }
                parentSet.add(location);
                parentCollectionMap.put(node, parentSet);
            }

            changes.add(type+" "+node+" "+location);
        } 

    }

    public void printNodes(){
        System.out.println("=================Occurences===================");
        Iterator<String> iter = nodeCountMap.keySet().iterator(); 
        while(iter.hasNext()) { 
            String key = iter.next();
            Integer value = nodeCountMap.get(key); 
            System.out.println(key + " : " + value.intValue()); 
        }

        System.out.println("=================Contexts===================");
        iter = parentCollectionMap.keySet().iterator(); 
        while(iter.hasNext()) { 
            String key = iter.next();
            Set<String> valueSet = parentCollectionMap.get(key);
            System.out.print("for "+key+":");
            Iterator<String> setIter = valueSet.iterator();
            while(setIter.hasNext()){
                System.out.print(setIter.next()+", ");
            }
            System.out.println(" ");
        }

    }

    public HashMap<String, Integer> getNodeCountMap() {
        return this.nodeCountMap;
    }

    public HashMap<String, Set<String>> getParentCollectionMap() {
        return this.parentCollectionMap;
    }

    public List<String> getChanges(){
        return this.changes;
    }
}