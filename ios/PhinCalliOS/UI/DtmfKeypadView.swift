import SwiftUI

struct DtmfKeypadView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var digitsEntered = ""
    let onSendDigit: (String) -> Void

    private let rows = [
        ["1", "2", "3"],
        ["4", "5", "6"],
        ["7", "8", "9"],
        ["*", "0", "#"]
    ]

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                Text(digitsEntered.isEmpty ? "Tap digits to transmit DTMF tones" : digitsEntered)
                    .font(.title3.monospaced())
                    .foregroundColor(.primary)
                    .frame(minHeight: 32)

                VStack(spacing: 12) {
                    ForEach(rows, id: \.self) { row in
                        HStack(spacing: 18) {
                            ForEach(row, id: \.self) { digit in
                                Button {
                                    digitsEntered.append(digit)
                                    onSendDigit(digit)
                                } label: {
                                    Text(digit)
                                        .font(.title2.weight(.semibold))
                                        .frame(width: 68, height: 68)
                                        .background(Color(red: 0.106, green: 0.165, blue: 0.255))
                                        .foregroundColor(.white)
                                        .clipShape(Circle())
                                }
                                .accessibilityLabel("DTMF digit \(digit)")
                            }
                        }
                    }
                }
                Spacer()
            }
            .padding(24)
            .navigationTitle("DTMF Keypad")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}
