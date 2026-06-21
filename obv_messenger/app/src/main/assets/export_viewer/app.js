/**
 * Olvid Export Viewer - Core Logic
 */

let appData = null;
let myId = null;
let senderNames = {};

// Helper: Convert Base64 SHA256 to Hex (same as SHA256 BytesKey in Olvid)
function b64ToHex(b64) {
    try {
        const bin = atob(b64);
        let hex = '';
        for (let i = 0; i < bin.length; i++) {
            const h = bin.charCodeAt(i).toString(16).toLowerCase();
            hex += (h.length === 1 ? '0' : '') + h;
        }
        return hex;
    } catch (e) {
        console.error("Base64 conversion error", e);
        return null;
    }
}

function formatDate(ts) {
    return new Date(ts).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

function formatTime(ts) {
    return new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

function linkify(text) {
    if (!text) return "";
    // First escape HTML
    const div = document.createElement('div');
    div.textContent = text;
    const escaped = div.innerHTML;

    // Replace URLs with links
    const urlPattern = /(\b(https?|ftp):\/\/[-A-Z0-9+&@#\/%?=~_|!:,.;]*[-A-Z0-9+&@#\/%=~_|])/ig;

    return escaped
        .replace(urlPattern, '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>');
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
}

// Renders a discussion title, falling back to an italic placeholder
// when the title is missing/empty (e.g. locked discussion with no custom name).
function displayTitle(title) {
    const trimmed = title == null ? '' : String(title).trim();
    return trimmed.length > 0
        ? escapeHtml(trimmed)
        : '<span class="untitled">(Untitled)</span>';
}

function initViewer(data) {
    appData = data;
    myId = data.bytesOwnedIdentity;
    
    // Hide landing, show app
    document.getElementById('landing').classList.add('hidden');
    document.getElementById('app-container').classList.remove('hidden');

    const discussions = data.discussions || [];
    senderNames = {};
    (data.contacts || []).forEach(c => {
        if (c.contact && c.displayName) {
            senderNames[c.contact] = c.displayName;
        }
    });

    const list = document.getElementById('list');
    list.innerHTML = '';

    discussions.forEach(d => {
        const item = document.createElement('div');
        item.className = 'discussion-item';
        item.dataset.id = d.discussion.id;
        item.innerHTML = `<div class="title">${displayTitle(d.title)}</div>`;
        item.onclick = () => loadChat(d.discussion.id, d.title);
        list.appendChild(item);
    });

    if (discussions.length > 0) {
        loadChat(discussions[0].discussion.id, discussions[0].title);
    }
}

function loadChat(id, title) {
    document.querySelectorAll('.discussion-item').forEach(el => 
        el.classList.toggle('active', el.dataset.id === id)
    );

    const allBatches = appData.messages || [];
    // Find all batches belonging to this discussion
    const discussionBatches = allBatches.filter(b => b.discussion.id === id);
    
    // Flatten all messages from all batches, injecting the sender ID from the batch level
    let allMessages = [];
    discussionBatches.forEach(batch => {
        const batchSender = batch.sender;
        const messagesWithSender = batch.messages.map(m => ({
            ...m,
            sender: batchSender
        }));
        allMessages = allMessages.concat(messagesWithSender);
    });

    const sorted = allMessages.sort((a, b) => a.timestamp - b.timestamp);
    
    const main = document.getElementById('main');
    main.innerHTML = `
        <div class="chat-header">
            <h2>${displayTitle(title)}</h2>
            <div class="meta">${sorted.length} messages</div>
        </div>
        <div class="messages-container" id="msgs">
            ${sorted.length === 0 ? '<div class="empty-chat" style="height: 100%"><h3>No messages in this discussion</h3></div>' : ''}
        </div>
    `;

    const container = document.getElementById('msgs');
    if (sorted.length === 0) return;
    let lastDate = '';

    sorted.forEach(msg => {
        const dateStr = formatDate(msg.timestamp);
        if (dateStr !== lastDate) {
            const sep = document.createElement('div');
            sep.className = 'date-separator';
            sep.innerHTML = `<span>${dateStr}</span>`;
            container.appendChild(sep);
            lastDate = dateStr;
        }

        const isMe = msg.sender === myId || !msg.sender;
        const row = document.createElement('div');
        row.className = `message-row ${isMe ? 'out' : 'in'}`;
        
        const senderDisplayName = senderNames[msg.sender] || '???';
        let content = `<div class="message-info">${senderDisplayName} • ${formatTime(msg.timestamp)}</div>`;
        if (msg.body) content += `<div class="bubble">${linkify(msg.body)}</div>`;
        
        if (msg.attachments && msg.attachments.length > 0) {
            content += '<div class="attachments">';
            msg.attachments.forEach(att => {
                content += '<div class="attachment-item">';
                // Handle filename resolution
                const diskFilename = att.sha256 ? b64ToHex(att.sha256) : att.filename;
                
                if (att.mimeType.startsWith('image/')) {
                    const fallbackSvg = "data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%2724%27 height=%2724%27 viewBox=%270 0 24 24%27 fill=%27none%27 stroke=%27%23ccc%27 stroke-width=%272%27 stroke-linecap=%27round%27 stroke-linejoin=%27round%27%3E%3Crect x=%273%27 y=%273%27 width=%2718%27 height=%2718%27 rx=%272%27 ry=%272%27%3E%3C/rect%3E%3Ccircle cx=%278.5%27 cy=%278.5%27 r=%271.5%27%3E%3C/circle%3E%3Cpolyline points=%2721 15 16 10 5 21%27%3E%3C/polyline%3E%3C/svg%3E";
                    content += `<img src="files/${diskFilename}" class="attachment-image" onerror="this.onerror=null; this.src='${fallbackSvg}';">`;
                } else if (att.mimeType.startsWith('video/')) {
                    content += `<video controls class="attachment-video">
                        <source src="files/${diskFilename}" type="${att.mimeType}">
                        <source src="files/${att.filename}" type="${att.mimeType}">
                        Your browser does not support the video tag.
                    </video>`;
                } else if (att.mimeType.startsWith('audio/')) {
                    // Try with converted hex filename, fallback to original
                    content += `<audio controls class="attachment-audio">
                        <source src="files/${diskFilename}" type="${att.mimeType}">
                        <source src="files/${att.filename}" type="${att.mimeType}">
                    </audio>`;
                } else {
                    content += `<a href="files/${diskFilename}" class="attachment-file" target="_blank">📄 ${att.filename}</a>`;
                }
                content += '</div>';
            });
            content += '</div>';
        }

        if (msg.location) {
            content += `<a href="https://maps.google.com/?q=${msg.location.lat},${msg.location.long}" class="location-link" target="_blank">📍 View Location</a>`;
        }

        row.innerHTML = content;
        container.appendChild(row);
    });
    container.scrollTop = container.scrollHeight;
}

// Handle File selection
document.getElementById('file-input').addEventListener('change', function(e) {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = function(e) {
        try {
            const data = JSON.parse(e.target.result);
            initViewer(data);
        } catch (err) {
            alert("Error parsing JSON file. Please make sure it's a valid Olvid export.");
            console.error(err);
        }
    };
    reader.readAsText(file);
});

// Setup Drag & Drop
const dropZone = document.getElementById('landing');
dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('drag-over'); });
dropZone.addEventListener('dragleave', () => { dropZone.classList.remove('drag-over'); });
dropZone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropZone.classList.remove('drag-over');
    const file = e.dataTransfer.files[0];
    if (file && file.name.endsWith('.json')) {
        document.getElementById('file-input').files = e.dataTransfer.files;
        document.getElementById('file-input').dispatchEvent(new Event('change'));
    }
});
