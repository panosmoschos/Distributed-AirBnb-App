import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Master {
    public static final int NUM_WORKERS = 3;
    private static List<Room> rooms = new ArrayList<>();
    private static Map<Integer, Socket> portSockets = new HashMap<>();
    private static Map<Integer, Integer> workerPorts = new HashMap<>(); // Map to store worker ports
    private static Map<Integer, String> workerHosts = new HashMap<>(); // Map to store worker host

    

    public static void main(String[] args) {

        //Initialize the rooms.
        String folderPath = "bin/rooms";
        rooms = Room.roomsOfFolder(folderPath);

        // Specify the port and host for each worker
        int[] ports = {8000, 8001, 8002};
        String [] hosts = {"localhost","localhost","192.168.1.46"};
        for (int i = 0; i < NUM_WORKERS; i++) {
            workerPorts.put(i, ports[i]); // Store worker port
            workerHosts.put(i,hosts[i]); //Store worker host
        }

        //Start the Master Server.
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Master Server started...");

            // Send rooms to workers
            sendRoomsToWorkers(rooms);

            while (true) {
                Socket socket = serverSocket.accept();
                ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
                // Check if the connection is from the Reducer or a new user.
                if (isConnectionFromReducer(socket,inputStream)) {
                    System.out.println("New REDUCER connection: " + socket);
                    portSockets.put(socket.getPort(), socket);
                    new ReducerHandler(socket,portSockets,inputStream).start();
                } else {
                    System.out.println("New USER connection: " + socket);
                    portSockets.put(socket.getPort(), socket);
                    new UserHandler(socket, workerPorts, workerHosts, inputStream).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Checks if the new connection is from a new user of the reducer.
    private static boolean isConnectionFromReducer(Socket socket, ObjectInputStream inputStream) {
       
       try {
            String type = inputStream.readUTF();
            if (type.equals("USER")){
                return false;
            }else if(type.equals("REDUCER")){
                return true;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }return false;
    }
    

    public static int hash(String roomName){ // simple hash function
        int hash=7;
        for (int i = 0; i < roomName.length(); i++) {
            hash = (hash*11 + roomName.charAt(i)) % 3;
        }
        return hash;
    }

    public static String getHost(Map<Integer, String> workerHosts, int workerID){
        return workerHosts.get(workerID);
    }

    private static void sendRoomsToWorkers(List<Room> rooms) {
        // Distribute rooms among workers
        for (Map.Entry<Integer, Integer> entry : workerPorts.entrySet()) {
            int workerId = entry.getKey();
            int port = entry.getValue();
            String host = getHost(workerHosts, workerId);
    
            // Get the subset of rooms assigned to this worker
            List<Room> workerRooms = getWorkerRooms(workerId, rooms);
    
            try (Socket socket = new Socket(host, port); //need SETTING if workers are on different hosts!!
                 ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
    
                // Send rooms to worker
                outputStream.writeObject(workerRooms);
                System.out.println("Sent rooms to Worker " + workerId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static List<Room> getWorkerRooms(int workerId, List<Room> allRooms) {
        List<Room> workerRooms = new ArrayList<>();
        for (Room room : allRooms) {
            if (hash(room.getRoomName()) % NUM_WORKERS == workerId) {
                workerRooms.add(room);
            }
        }
        return workerRooms;
    }
}