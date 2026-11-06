(function () {
  var dataEl = document.getElementById("choice-data");
  var payloadEl = document.getElementById("choice-payload");
  var listEl = document.getElementById("ability-list");
  var confirmEl = document.getElementById("asi-confirm");
  var msgEl = document.getElementById("asi-msg");
  if (!dataEl || !payloadEl || !listEl || !confirmEl) return;

  function start() {
    var data = {};
    try {
      data = JSON.parse(dataEl.value || "{}");
    } catch (err) {
      data = {};
    }
    var scores = data.abilities || {};
    var abilities = ["str", "dex", "con", "int", "wis", "cha"];
    var mode = "plus2";
    var picked = [];

    function needed() {
      return mode === "plus2" ? 1 : 2;
    }

    function render() {
      listEl.innerHTML = "";
      for (var i = 0; i < abilities.length; i++) {
        (function (key) {
          var active = picked.indexOf(key) >= 0;
          var card = document.createElement("button");
          card.setAttribute("type", "button");
          card.setAttribute("class", "choice-card" + (active ? " active" : ""));
          var value = Number(scores[key] || 10);
          card.innerHTML =
            '<span class="card-title"><translation>ability.dndclasses.' + key + '</translation></span>' +
            '<span class="card-sub">' + value + ' -> ' + Math.min(20, value + (mode === "plus2" ? 2 : 1)) + '</span>';
          card.addEventListener("click", function () {
            var index = picked.indexOf(key);
            if (index >= 0) {
              picked.splice(index, 1);
            } else if (picked.length < needed()) {
              picked.push(key);
            }
            render();
          });
          listEl.appendChild(card);
        })(abilities[i]);
      }
      if (picked.length === needed()) {
        confirmEl.removeAttribute("disabled");
      } else {
        confirmEl.setAttribute("disabled", "disabled");
      }
    }

    var plus2 = document.getElementById("mode-plus2");
    var plus1 = document.getElementById("mode-plus1");
    if (plus2) {
      plus2.addEventListener("click", function () {
        mode = "plus2";
        picked = [];
        plus2.setAttribute("class", "mode-btn active");
        if (plus1) plus1.setAttribute("class", "mode-btn");
        render();
      });
    }
    if (plus1) {
      plus1.addEventListener("click", function () {
        mode = "plus1plus1";
        picked = [];
        plus1.setAttribute("class", "mode-btn active");
        if (plus2) plus2.setAttribute("class", "mode-btn");
        render();
      });
    }

    confirmEl.addEventListener("click", function () {
      if (picked.length !== needed()) return;
      payloadEl.value = JSON.stringify({ mode: mode, abilities: picked });
    });

    if (msgEl) {
      msgEl.innerHTML = "<translation>screen.dndclasses.asi.pending</translation> " + (data.pending || 1);
    }
    render();
  }

  if (dataEl.value) {
    start();
  } else {
    var timer = setInterval(function () {
      if (dataEl.value) {
        clearInterval(timer);
        start();
      }
    }, 50);
  }
})();
