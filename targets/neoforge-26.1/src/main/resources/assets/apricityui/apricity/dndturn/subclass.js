(function () {
  var dataEl = document.getElementById("choice-data");
  var payloadEl = document.getElementById("choice-payload");
  var listEl = document.getElementById("subclass-list");
  if (!dataEl || !payloadEl || !listEl) return;

  function start() {
    var data = {};
    try {
      data = JSON.parse(dataEl.value || "{}");
    } catch (err) {
      data = {};
    }

    var options = data.options || [];
    for (var i = 0; i < options.length; i++) {
      (function (option) {
        var button = document.createElement("button");
        button.setAttribute("type", "button");
        button.setAttribute("class", "choice-card");
        button.innerHTML =
          '<span class="card-title"><translation>' + option.name + '</translation></span>' +
          '<span class="card-sub">' + option.id + '</span>';
        button.addEventListener("click", function () {
          payloadEl.value = option.id;
        });
        listEl.appendChild(button);
      })(options[i]);
    }
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
