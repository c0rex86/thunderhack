from flask import Flask, jsonify, request
import requests
from datetime import datetime
import time
import threading
import re

app = Flask(__name__)
#мама, хде я
online_data = {
    "online": 0,
    "max_online": 0,
    "players": set(), 
    "total_players": set(),
    "last_update": 0,
    "stats": {
        "total_joins": 0,
        "peak_online": 0,
        "peak_online_time": None
    }
}

def get_commit_type(message):
    message = message.lower()
    if any(x in message for x in ["add", "added", "new", "implement"]):
        return "[+]", "GREEN"
    elif any(x in message for x in ["remove", "delete", "deleted", "cleanup"]):
        return "[-]", "RED"
    elif any(x in message for x in ["fix", "fixed", "patch", "bug"]):
        return "[/]", "LIGHT_PURPLE"
    elif any(x in message for x in ["update", "change", "modify"]):
        return "[*]", "YELLOW"
    return "[*]", "GOLD"

def format_commit_message(message):
    message = re.sub(r'#\d+', '', message)
    message = re.sub(r'\(.*?\)', '', message)
    message = message.strip()
    if message:
        message = message[0].upper() + message[1:]
    return message

def update_cache():
    while True:
        try:
            response = requests.get('https://apicheat.c0rex86.ru/online')
            if response.status_code == 200:
                data = response.json()
                online_data["online"] = data["online"]
                online_data["max_online"] = data["max_online"]
            
            changelog_response = requests.get('https://apicheat.c0rex86.ru/changelog')
            if changelog_response.status_code == 200:
                cache["changelog"] = changelog_response.json()
                
        except Exception:
            pass
            
        time.sleep(30)  

update_thread = threading.Thread(target=update_cache, daemon=True)
update_thread.start()

@app.route('/online', methods=['GET'])
def get_online():
    current_time = time.time()
    to_remove = set()
    for player in online_data["players"]:
        if current_time - player[1] > 120:  
            to_remove.add(player)
    online_data["players"] -= to_remove
    
    current_online = len(online_data["players"])
    online_data["online"] = current_online
    
    
    if current_online > online_data["stats"]["peak_online"]:
        online_data["stats"]["peak_online"] = current_online
        online_data["stats"]["peak_online_time"] = datetime.now().strftime("%d.%m.%Y %H:%M")
    
    online_data["max_online"] = max(online_data["max_online"], current_online)
    
    return jsonify({
        "online": online_data["online"],
        "max_online": online_data["max_online"],
        "stats": online_data["stats"]
    })

@app.route('/online', methods=['POST'])
def update_online():
    name = request.args.get('name', '')
    if name:
        online_data["players"].add((name, time.time()))
        online_data["total_players"].add(name)
        online_data["stats"]["total_joins"] += 1
        return jsonify({"status": "success"})
    return jsonify({"status": "error", "message": "No name provided"}), 400

@app.route('/users/online')
def get_online_users():
    current_time = time.time()
    active_players = [player[0] for player in online_data["players"] if current_time - player[1] <= 120]
    return jsonify(active_players)

@app.route('/users')
def get_all_users():
    return jsonify(list(online_data["total_players"]))

@app.route('/stats')
def get_stats():
    return jsonify({
        "total_players": len(online_data["total_players"]),
        "total_joins": online_data["stats"]["total_joins"],
        "peak_online": online_data["stats"]["peak_online"],
        "peak_online_time": online_data["stats"]["peak_online_time"]
    })

@app.route('/changelog')
def get_changelog():
    try:
        response = requests.get('https://api.github.com/repos/c0rex86/thunderhack/commits')
        if response.status_code == 200:
            commits = response.json()
            changelog = []
            for commit in commits[:10]:
                message = commit["commit"]["message"]
                date = commit["commit"]["author"]["date"]
                author = commit["commit"]["author"]["name"]
                
                # Форматируем сообщение
                message = format_commit_message(message)
                prefix, color = get_commit_type(message)
                
                # Форматируем дату
                commit_date = datetime.strptime(date, "%Y-%m-%dT%H:%M:%SZ")
                formatted_date = commit_date.strftime("%d.%m.%Y %H:%M")
                
                changelog.append({
                    "message": f"{prefix} {message}",
                    "date": formatted_date,
                    "author": author,
                    "color": color
                })
            return jsonify(changelog)
    except Exception:
        pass
    return jsonify([{"message": "[*] No changelog available", "color": "GOLD", "date": datetime.now().strftime("%d.%m.%Y %H:%M"), "author": "System"}])

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000) 