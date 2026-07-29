Feature: Capture pipeline — frame capture and landmark extraction
  As the PEA runtime
  I want to capture frames from the host machine webcam and extract facial landmarks
  So that the classification pipeline has valid input

  Background:
    Given the PEA is configured with device_index=0 and resolution=640x480
    And MediaPipe FaceMesh is initialised in video mode

  Scenario: Camera opens on configured device index
    When the CaptureThread starts
    Then VideoCapture is opened on device index 0
    And the capture loop produces frames at approximately 15 fps

  Scenario: Frames are dropped when queue is full without blocking capture loop
    Given the frame queue is at capacity (maxsize=5)
    When a new frame arrives from VideoCapture
    Then the frame is dropped
    And the capture loop continues without blocking
    And no exception is raised

  Scenario: MediaPipe produces LandmarkSet when a face is detected
    Given a frame containing a visible face
    When the LandmarkExtractor processes the frame
    Then a LandmarkSet with 468 normalized coordinates is produced
    And the LandmarkSet is placed on the classification queue

  Scenario: No LandmarkSet is produced when no face is in the frame
    Given a frame with no visible face
    When the LandmarkExtractor processes the frame
    Then no LandmarkSet is produced
    And only a DEBUG log event is emitted

  @lgpd
  Scenario: No frame data is written to disk under any condition
    When the capture pipeline processes 100 frames
    Then no frame file is created in the data directory
    And no landmark coordinates appear in any log file

  Scenario: PEA exits with code 1 when camera disconnects
    Given the capture loop is running
    When VideoCapture.read() returns False
    Then a CRITICAL log event is emitted with component=capture
    And the process exits with code 1
