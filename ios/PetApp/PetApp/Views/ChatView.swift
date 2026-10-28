import SwiftUI

/// 对话页：对话框 + 语料回复（P1-2，双端语料一致）。
struct ChatView: View {

    @EnvironmentObject private var environment: AppEnvironment

    /// 单条消息。
    struct Message: Identifiable {
        let id = UUID()
        let sender: String
        let text: String
        let fromPet: Bool
    }

    @State private var messages: [Message] = [
        Message(sender: "小希", text: "嗨~今天想聊点什么？", fromPet: true)
    ]
    @State private var inputText: String = ""

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(messages) { message in
                            bubble(message)
                                .id(message.id)
                        }
                    }
                    .padding(12)
                }
                .onChange(of: messages.count) { _ in
                    if let last = messages.last {
                        withAnimation {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                }
            }

            HStack(spacing: 8) {
                TextField("和小希说点什么…", text: $inputText)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(send)
                Button(action: send) {
                    Image(systemName: "paperplane.fill")
                }
                .disabled(inputText.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(12)
        }
        .navigationTitle("💬 聊天")
    }

    private func send() {
        let input = inputText.trimmingCharacters(in: .whitespaces)
        guard !input.isEmpty else { return }
        inputText = ""
        messages.append(Message(sender: "你", text: input, fromPet: false))

        Task {
            // 聊天也是互动：点按增益 + 刷新 lastActiveAt（共享知识 #9）
            _ = await environment.repository.recordInteraction()
            let reply = environment.dialogueEngine.respond(input)
            messages.append(Message(sender: "小希", text: reply.text, fromPet: true))
        }
    }

    /// 消息气泡（宠物右侧紫色 / 用户左侧灰色）。
    private func bubble(_ message: Message) -> some View {
        HStack {
            if message.fromPet {
                Spacer(minLength: 40)
            }
            Text(message.fromPet ? "🐱 \(message.text)" : message.text)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    message.fromPet ? Color.purple.opacity(0.85) : Color(.systemGray5),
                    in: RoundedRectangle(cornerRadius: 16)
                )
                .foregroundStyle(message.fromPet ? Color.white : Color.primary)
            if !message.fromPet {
                Spacer(minLength: 40)
            }
        }
    }
}
