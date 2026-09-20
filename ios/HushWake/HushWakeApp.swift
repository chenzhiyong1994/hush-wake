import SwiftUI

@main
struct HushWakeApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(model)
                .preferredColorScheme(.dark)
                .tint(Color(red: 0.96, green: 0.75, blue: 0.44))
                .onChange(of: phase) { _, value in model.scene(value) }
                .onAppear { model.scene(phase) }
        }
    }
}
