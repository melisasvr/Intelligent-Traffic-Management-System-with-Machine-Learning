import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

// Vehicle Detection and Classification (Enhanced)
class Vehicle {
    private String id;
    private VehicleType type;
    private double speed;
    private String location;
    private LocalDateTime detectionTime;
    private String direction; // N, S, E, W
    
    public enum VehicleType {
        CAR, TRUCK, MOTORCYCLE, BUS, EMERGENCY
    }
    
    public Vehicle(String id, VehicleType type, double speed, String location, String direction) {
        this.id = id;
        this.type = type;
        this.speed = speed;
        this.location = location;
        this.direction = direction;
        this.detectionTime = LocalDateTime.now();
    }
    
    // Getters
    public String getId() { return id; }
    public VehicleType getType() { return type; }
    public double getSpeed() { return speed; }
    public String getLocation() { return location; }
    public String getDirection() { return direction; }
    public LocalDateTime getDetectionTime() { return detectionTime; }
    
    public boolean isEmergencyVehicle() {
        return type == VehicleType.EMERGENCY;
    }
}

// Traffic Data Point for ML
class TrafficDataPoint {
    private LocalDateTime timestamp;
    private String intersectionId;
    private int vehicleCount;
    private double avgSpeed;
    private int hour;
    private int dayOfWeek;
    private double congestionLevel;
    private Map<String, Integer> directionFlow; // N, S, E, W flow counts
    
    public TrafficDataPoint(String intersectionId, List<Vehicle> vehicles) {
        this.timestamp = LocalDateTime.now();
        this.intersectionId = intersectionId;
        this.vehicleCount = vehicles.size();
        this.avgSpeed = vehicles.stream().mapToDouble(Vehicle::getSpeed).average().orElse(0);
        this.hour = timestamp.getHour();
        this.dayOfWeek = timestamp.getDayOfWeek().getValue();
        this.congestionLevel = calculateCongestionLevel(vehicles);
        this.directionFlow = calculateDirectionFlow(vehicles);
    }
    
    private double calculateCongestionLevel(List<Vehicle> vehicles) {
        if (vehicles.isEmpty()) return 0.0;
        
        // Consider vehicle count, average speed, and density
        double densityFactor = Math.min(vehicles.size() / 20.0, 1.0); // Normalize to 20 vehicles max
        double speedFactor = Math.max(0, (60 - avgSpeed) / 60.0); // Lower speed = higher congestion
        
        return (densityFactor * 0.6 + speedFactor * 0.4) * 100; // 0-100 scale
    }
    
    private Map<String, Integer> calculateDirectionFlow(List<Vehicle> vehicles) {
        Map<String, Integer> flow = new HashMap<>();
        flow.put("N", 0); flow.put("S", 0); flow.put("E", 0); flow.put("W", 0);
        
        for (Vehicle vehicle : vehicles) {
            flow.merge(vehicle.getDirection(), 1, Integer::sum);
        }
        return flow;
    }
    
    // Getters
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getIntersectionId() { return intersectionId; }
    public int getVehicleCount() { return vehicleCount; }
    public double getAvgSpeed() { return avgSpeed; }
    public int getHour() { return hour; }
    public int getDayOfWeek() { return dayOfWeek; }
    public double getCongestionLevel() { return congestionLevel; }
    public Map<String, Integer> getDirectionFlow() { return directionFlow; }
}

// Simple ML Model for Traffic Prediction
class TrafficPredictionModel {
    private Map<String, List<TrafficDataPoint>> historicalData;
    private Map<String, Double> hourlyPatterns;
    private Map<String, Double> weeklyPatterns;
    
    public TrafficPredictionModel() {
        this.historicalData = new ConcurrentHashMap<>();
        this.hourlyPatterns = new ConcurrentHashMap<>();
        this.weeklyPatterns = new ConcurrentHashMap<>();
    }
    
    public void trainModel(String intersectionId, List<TrafficDataPoint> data) {
        historicalData.put(intersectionId, new ArrayList<>(data));
        
        // Calculate hourly patterns
        Map<Integer, List<Double>> hourlyGroups = data.stream()
            .collect(Collectors.groupingBy(
                TrafficDataPoint::getHour,
                Collectors.mapping(TrafficDataPoint::getCongestionLevel, Collectors.toList())
            ));
        
        for (Map.Entry<Integer, List<Double>> entry : hourlyGroups.entrySet()) {
            double avgCongestion = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            hourlyPatterns.put(intersectionId + "_hour_" + entry.getKey(), avgCongestion);
        }
        
        // Calculate weekly patterns
        Map<Integer, List<Double>> weeklyGroups = data.stream()
            .collect(Collectors.groupingBy(
                TrafficDataPoint::getDayOfWeek,
                Collectors.mapping(TrafficDataPoint::getCongestionLevel, Collectors.toList())
            ));
        
        for (Map.Entry<Integer, List<Double>> entry : weeklyGroups.entrySet()) {
            double avgCongestion = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            weeklyPatterns.put(intersectionId + "_day_" + entry.getKey(), avgCongestion);
        }
    }
    
    public TrafficPrediction predictTraffic(String intersectionId, int hoursAhead) {
        LocalDateTime futureTime = LocalDateTime.now().plusHours(hoursAhead);
        int futureHour = futureTime.getHour();
        int futureDayOfWeek = futureTime.getDayOfWeek().getValue();
        
        // Get historical patterns
        double hourlyPattern = hourlyPatterns.getOrDefault(intersectionId + "_hour_" + futureHour, 50.0);
        double weeklyPattern = weeklyPatterns.getOrDefault(intersectionId + "_day_" + futureDayOfWeek, 50.0);
        
        // Simple weighted average prediction
        double predictedCongestion = (hourlyPattern * 0.7 + weeklyPattern * 0.3);
        
        // Add some randomness to simulate real-world variability
        Random random = new Random();
        predictedCongestion += (random.nextGaussian() * 5); // ±5% variance
        predictedCongestion = Math.max(0, Math.min(100, predictedCongestion));
        
        int predictedVehicleCount = (int) (predictedCongestion / 5); // Rough conversion
        double predictedAvgSpeed = Math.max(20, 60 - (predictedCongestion * 0.4));
        
        return new TrafficPrediction(intersectionId, futureTime, predictedCongestion, 
                                   predictedVehicleCount, predictedAvgSpeed);
    }
    
    public List<String> getTrafficRecommendations(String intersectionId) {
        List<String> recommendations = new ArrayList<>();
        
        // Analyze current patterns
        List<TrafficDataPoint> data = historicalData.get(intersectionId);
        if (data == null || data.isEmpty()) {
            recommendations.add("Insufficient data for recommendations");
            return recommendations;
        }
        
        // Find peak hours
        Map<Integer, Double> hourlyAvg = data.stream()
            .collect(Collectors.groupingBy(
                TrafficDataPoint::getHour,
                Collectors.averagingDouble(TrafficDataPoint::getCongestionLevel)
            ));
        
        List<Integer> peakHours = hourlyAvg.entrySet().stream()
            .filter(entry -> entry.getValue() > 70)
            .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (!peakHours.isEmpty()) {
            recommendations.add("Peak congestion hours: " + peakHours.stream()
                .map(h -> h + ":00").collect(Collectors.joining(", ")));
            recommendations.add("Consider extending green light duration during peak hours");
        }
        
        // Analyze direction flow
        Map<String, Double> avgDirectionFlow = new HashMap<>();
        for (TrafficDataPoint point : data) {
            for (Map.Entry<String, Integer> flow : point.getDirectionFlow().entrySet()) {
                avgDirectionFlow.merge(flow.getKey(), flow.getValue().doubleValue(), Double::sum);
            }
        }
        
        String dominantDirection = avgDirectionFlow.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
        
        recommendations.add("Dominant traffic flow direction: " + dominantDirection);
        recommendations.add("Consider asymmetric signal timing favoring " + dominantDirection + " direction");
        
        return recommendations;
    }
}

// Traffic Prediction Result
class TrafficPrediction {
    private String intersectionId;
    private LocalDateTime predictedTime;
    private double predictedCongestionLevel;
    private int predictedVehicleCount;
    private double predictedAvgSpeed;
    private double confidence;
    
    public TrafficPrediction(String intersectionId, LocalDateTime predictedTime, 
                           double congestionLevel, int vehicleCount, double avgSpeed) {
        this.intersectionId = intersectionId;
        this.predictedTime = predictedTime;
        this.predictedCongestionLevel = congestionLevel;
        this.predictedVehicleCount = vehicleCount;
        this.predictedAvgSpeed = avgSpeed;
        this.confidence = calculateConfidence(congestionLevel);
    }
    
    private double calculateConfidence(double congestionLevel) {
        // Simple confidence calculation based on how "normal" the prediction is
        if (congestionLevel >= 20 && congestionLevel <= 80) {
            return 0.8 + (Math.random() * 0.15); // 80-95% confidence for normal ranges
        } else {
            return 0.6 + (Math.random() * 0.2); // 60-80% confidence for extreme values
        }
    }
    
    // Getters
    public String getIntersectionId() { return intersectionId; }
    public LocalDateTime getPredictedTime() { return predictedTime; }
    public double getPredictedCongestionLevel() { return predictedCongestionLevel; }
    public int getPredictedVehicleCount() { return predictedVehicleCount; }
    public double getPredictedAvgSpeed() { return predictedAvgSpeed; }
    public double getConfidence() { return confidence; }
    
    public String getCongestionCategory() {
        if (predictedCongestionLevel < 30) return "LOW";
        else if (predictedCongestionLevel < 70) return "MODERATE";
        else return "HIGH";
    }
}

// Enhanced Traffic Signal with ML-based optimization
class TrafficSignal {
    private String intersectionId;
    private SignalState currentState;
    private int greenDuration;
    private int redDuration;
    private LocalDateTime lastStateChange;
    private boolean emergencyOverride;
    private TrafficPrediction upcomingPrediction;
    
    public enum SignalState {
        RED, YELLOW, GREEN
    }
    
    public TrafficSignal(String intersectionId) {
        this.intersectionId = intersectionId;
        this.currentState = SignalState.RED;
        this.greenDuration = 30;
        this.redDuration = 30;
        this.lastStateChange = LocalDateTime.now();
        this.emergencyOverride = false;
    }
    
    public void adaptTimingWithML(int currentVehicleCount, double avgSpeed, TrafficPrediction prediction) {
        // Original adaptive logic
        if (currentVehicleCount > 10) {
            greenDuration = Math.min(60, greenDuration + 10);
        } else if (currentVehicleCount < 3) {
            greenDuration = Math.max(15, greenDuration - 5);
        }
        
        if (avgSpeed < 20) {
            greenDuration += 5;
        }
        
        // ML-based optimization
        if (prediction != null) {
            this.upcomingPrediction = prediction;
            
            // Proactive adjustment based on prediction
            if (prediction.getCongestionCategory().equals("HIGH")) {
                greenDuration = Math.min(90, greenDuration + 15);
                redDuration = Math.max(20, redDuration - 5);
            } else if (prediction.getCongestionCategory().equals("LOW")) {
                greenDuration = Math.max(20, greenDuration - 10);
                redDuration = Math.min(45, redDuration + 5);
            }
        }
    }
    
    public void updateState() {
        if (emergencyOverride) return;
        
        long secondsSinceChange = java.time.Duration.between(lastStateChange, LocalDateTime.now()).getSeconds();
        
        switch (currentState) {
            case GREEN:
                if (secondsSinceChange >= greenDuration) {
                    currentState = SignalState.YELLOW;
                    lastStateChange = LocalDateTime.now();
                }
                break;
            case YELLOW:
                if (secondsSinceChange >= 3) {
                    currentState = SignalState.RED;
                    lastStateChange = LocalDateTime.now();
                }
                break;
            case RED:
                if (secondsSinceChange >= redDuration) {
                    currentState = SignalState.GREEN;
                    lastStateChange = LocalDateTime.now();
                }
                break;
        }
    }
    
    public void setEmergencyOverride(boolean override) {
        this.emergencyOverride = override;
        if (override) {
            this.currentState = SignalState.GREEN;
            this.lastStateChange = LocalDateTime.now();
        }
    }
    
    // Getters
    public String getIntersectionId() { return intersectionId; }
    public SignalState getCurrentState() { return currentState; }
    public int getGreenDuration() { return greenDuration; }
    public boolean isEmergencyOverride() { return emergencyOverride; }
    public TrafficPrediction getUpcomingPrediction() { return upcomingPrediction; }
}

// Enhanced Vehicle Detection System
class VehicleDetectionSystem {
    private Random random = new Random();
    private String[] directions = {"N", "S", "E", "W"};
    
    public List<Vehicle> detectVehicles(String intersectionId) {
        List<Vehicle> detectedVehicles = new ArrayList<>();
        
        // Simulate more realistic traffic patterns based on time
        int hour = LocalDateTime.now().getHour();
        int baseVehicleCount = getRealisticVehicleCount(hour);
        int vehicleCount = Math.max(1, baseVehicleCount + random.nextInt(5) - 2);
        
        for (int i = 0; i < vehicleCount; i++) {
            String vehicleId = "V" + System.currentTimeMillis() + "_" + i;
            Vehicle.VehicleType type = getRandomVehicleType();
            double speed = getRealisticSpeed(hour, type);
            String direction = directions[random.nextInt(directions.length)];
            
            detectedVehicles.add(new Vehicle(vehicleId, type, speed, intersectionId, direction));
        }
        
        return detectedVehicles;
    }
    
    private int getRealisticVehicleCount(int hour) {
        // Simulate rush hour patterns
        if (hour >= 7 && hour <= 9) return 12 + random.nextInt(8); // Morning rush
        else if (hour >= 17 && hour <= 19) return 10 + random.nextInt(8); // Evening rush
        else if (hour >= 12 && hour <= 14) return 8 + random.nextInt(4); // Lunch time
        else if (hour >= 22 || hour <= 5) return 2 + random.nextInt(3); // Late night
        else return 5 + random.nextInt(6); // Normal hours
    }
    
    private double getRealisticSpeed(int hour, Vehicle.VehicleType type) {
        double baseSpeed = 35; // Base speed in km/h
        
        // Adjust for rush hour (slower traffic)
        if ((hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19)) {
            baseSpeed *= 0.7; // 30% slower during rush hour
        }
        
        // Adjust for vehicle type
        switch (type) {
            case TRUCK: baseSpeed *= 0.8; break;
            case BUS: baseSpeed *= 0.85; break;
            case MOTORCYCLE: baseSpeed *= 1.2; break;
            case EMERGENCY: baseSpeed *= 1.5; break;
            case CAR: 
            default: 
                // CAR and default case - no adjustment needed
                break;
        }
        
        return Math.max(15, baseSpeed + (random.nextGaussian() * 10));
    }
    
    private Vehicle.VehicleType getRandomVehicleType() {
        double rand = random.nextDouble();
        if (rand < 0.02) return Vehicle.VehicleType.EMERGENCY;
        else if (rand < 0.07) return Vehicle.VehicleType.BUS;
        else if (rand < 0.12) return Vehicle.VehicleType.TRUCK;
        else if (rand < 0.25) return Vehicle.VehicleType.MOTORCYCLE;
        else return Vehicle.VehicleType.CAR;
    }
}

// Enhanced Traffic Analytics with ML
class TrafficAnalytics {
    private Map<String, List<Vehicle>> trafficHistory;
    private Map<String, Integer> congestionMap;
    private Map<String, List<TrafficDataPoint>> mlTrainingData;
    private TrafficPredictionModel predictionModel;
    
    public TrafficAnalytics() {
        this.trafficHistory = new ConcurrentHashMap<>();
        this.congestionMap = new ConcurrentHashMap<>();
        this.mlTrainingData = new ConcurrentHashMap<>();
        this.predictionModel = new TrafficPredictionModel();
    }
    
    public void recordTrafficData(String intersectionId, List<Vehicle> vehicles) {
        trafficHistory.computeIfAbsent(intersectionId, k -> new ArrayList<>()).addAll(vehicles);
        congestionMap.put(intersectionId, vehicles.size());
        
        // Create ML training data point
        TrafficDataPoint dataPoint = new TrafficDataPoint(intersectionId, vehicles);
        mlTrainingData.computeIfAbsent(intersectionId, k -> new ArrayList<>()).add(dataPoint);
        
        // Train model periodically (every 10 data points)
        List<TrafficDataPoint> intersectionData = mlTrainingData.get(intersectionId);
        if (intersectionData.size() % 10 == 0) {
            predictionModel.trainModel(intersectionId, intersectionData);
        }
    }
    
    public TrafficPrediction getPrediction(String intersectionId, int hoursAhead) {
        return predictionModel.predictTraffic(intersectionId, hoursAhead);
    }
    
    public List<String> getMLRecommendations(String intersectionId) {
        return predictionModel.getTrafficRecommendations(intersectionId);
    }
    
    public double getAverageSpeed(String intersectionId) {
        List<Vehicle> vehicles = trafficHistory.get(intersectionId);
        if (vehicles == null || vehicles.isEmpty()) return 0;
        
        return vehicles.stream()
                .mapToDouble(Vehicle::getSpeed)
                .average()
                .orElse(0);
    }
    
    public List<String> getCongestionHotspots() {
        return congestionMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public void generateEnhancedTrafficReport() {
        System.out.println("\n=== ENHANCED TRAFFIC ANALYTICS REPORT WITH ML ===");
        System.out.println("Generated at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        System.out.println("\nCurrent Traffic Status:");
        List<String> hotspots = getCongestionHotspots();
        for (String intersection : hotspots) {
            System.out.printf("- %s: %d vehicles (Avg Speed: %.1f km/h)%n", 
                intersection, congestionMap.get(intersection), getAverageSpeed(intersection));
        }
        
        System.out.println("\nTraffic Predictions (Next Hour):");
        for (String intersection : trafficHistory.keySet()) {
            TrafficPrediction prediction1h = getPrediction(intersection, 1);
            
            if (prediction1h != null) {
                System.out.printf("- %s (+1h): %s congestion (%.0f%% confidence)%n", 
                    intersection, prediction1h.getCongestionCategory(), prediction1h.getConfidence() * 100);
                System.out.printf("  Expected: %d vehicles at %.1f km/h avg%n", 
                    prediction1h.getPredictedVehicleCount(), prediction1h.getPredictedAvgSpeed());
            }
        }
        
        System.out.println("\nML-Based Recommendations:");
        for (String intersection : trafficHistory.keySet()) {
            List<String> recommendations = getMLRecommendations(intersection);
            if (!recommendations.isEmpty()) {
                System.out.println("- " + intersection + ":");
                for (String rec : recommendations) {
                    System.out.println("  • " + rec);
                }
            }
        }
        
        System.out.println("\nSystem Statistics:");
        System.out.println("Total Intersections Monitored: " + trafficHistory.size());
        int totalVehicles = trafficHistory.values().stream().mapToInt(List::size).sum();
        System.out.println("Total Vehicles Detected: " + totalVehicles);
        int totalDataPoints = mlTrainingData.values().stream().mapToInt(List::size).sum();
        System.out.println("ML Training Data Points: " + totalDataPoints);
    }
}

// Emergency Vehicle Handler (unchanged)
class EmergencyVehicleHandler {
    private TrafficManagementSystem tms;
    
    public EmergencyVehicleHandler(TrafficManagementSystem tms) {
        this.tms = tms;
    }
    
    public void handleEmergencyVehicle(Vehicle emergencyVehicle) {
        if (!emergencyVehicle.isEmergencyVehicle()) return;
        
        System.out.println("*** EMERGENCY VEHICLE DETECTED: " + emergencyVehicle.getId() + 
                          " at " + emergencyVehicle.getLocation());
        
        String intersectionId = emergencyVehicle.getLocation();
        tms.setEmergencyOverride(intersectionId, true);
        
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                tms.setEmergencyOverride(intersectionId, false);
                System.out.println(">>> Emergency override cleared for " + intersectionId);
            }
        }, 30000);
    }
}

// Enhanced Main Traffic Management System
class TrafficManagementSystem {
    private Map<String, TrafficSignal> trafficSignals;
    private VehicleDetectionSystem detectionSystem;
    private TrafficAnalytics analytics;
    private EmergencyVehicleHandler emergencyHandler;
    private ScheduledExecutorService scheduler;
    
    public TrafficManagementSystem() {
        this.trafficSignals = new ConcurrentHashMap<>();
        this.detectionSystem = new VehicleDetectionSystem();
        this.analytics = new TrafficAnalytics();
        this.emergencyHandler = new EmergencyVehicleHandler(this);
        this.scheduler = Executors.newScheduledThreadPool(4);
        
        initializeIntersections();
    }
    
    private void initializeIntersections() {
        String[] intersections = {"Main_St_1st_Ave", "Oak_St_2nd_Ave", "Pine_St_3rd_Ave", "Elm_St_4th_Ave"};
        for (String intersection : intersections) {
            trafficSignals.put(intersection, new TrafficSignal(intersection));
        }
    }
    
    public void startSystem() {
        System.out.println("[SYSTEM] Starting Enhanced Intelligent Traffic Management System with ML...");
        
        scheduler.scheduleAtFixedRate(this::updateTrafficSignals, 0, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::detectAndProcessVehicles, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(analytics::generateEnhancedTrafficReport, 45, 45, TimeUnit.SECONDS);
        
        System.out.println("[SYSTEM] Enhanced Traffic Management System with ML is now active!");
    }
    
    private void updateTrafficSignals() {
        for (TrafficSignal signal : trafficSignals.values()) {
            signal.updateState();
        }
    }
    
    private void detectAndProcessVehicles() {
        for (String intersectionId : trafficSignals.keySet()) {
            List<Vehicle> detectedVehicles = detectionSystem.detectVehicles(intersectionId);
            
            analytics.recordTrafficData(intersectionId, detectedVehicles);
            
            for (Vehicle vehicle : detectedVehicles) {
                if (vehicle.isEmergencyVehicle()) {
                    emergencyHandler.handleEmergencyVehicle(vehicle);
                }
            }
            
            // Enhanced adaptive signal timing with ML
            TrafficSignal signal = trafficSignals.get(intersectionId);
            double avgSpeed = analytics.getAverageSpeed(intersectionId);
            TrafficPrediction prediction = analytics.getPrediction(intersectionId, 1);
            
            signal.adaptTimingWithML(detectedVehicles.size(), avgSpeed, prediction);
            
            displayEnhancedIntersectionStatus(intersectionId, signal, detectedVehicles.size());
        }
    }
    
    private void displayEnhancedIntersectionStatus(String intersectionId, TrafficSignal signal, int vehicleCount) {
        String statusIcon = getSignalIcon(signal.getCurrentState());
        String emergencyStatus = signal.isEmergencyOverride() ? " [EMERGENCY]" : "";
        
        String predictionStatus = "";
        if (signal.getUpcomingPrediction() != null) {
            TrafficPrediction pred = signal.getUpcomingPrediction();
            predictionStatus = String.format(" [PRED: %s]", pred.getCongestionCategory());
        }
        
        System.out.printf("%s %s: %s (%ds) - %d vehicles%s%s%n",
            statusIcon, intersectionId, signal.getCurrentState(), 
            signal.getGreenDuration(), vehicleCount, emergencyStatus, predictionStatus);
    }
    
    private String getSignalIcon(TrafficSignal.SignalState state) {
        switch (state) {
            case RED: return "[🔴]";
            case YELLOW: return "[🟡]";
            case GREEN: return "[🟢]";
            default: return "[UNKNOWN]";
        }
    }
    
    public void setEmergencyOverride(String intersectionId, boolean override) {
        TrafficSignal signal = trafficSignals.get(intersectionId);
        if (signal != null) {
            signal.setEmergencyOverride(override);
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        System.out.println("[SYSTEM] Enhanced Traffic Management System shutdown complete.");
    }
}

// Main Application
public class IntelligentTrafficManagementSystem {
    public static void main(String[] args) {
        TrafficManagementSystem tms = new TrafficManagementSystem();
        
        tms.startSystem();
        
        try {
            Thread.sleep(120000); // Run for 2 minutes to see ML in action
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        tms.shutdown();
    }
}