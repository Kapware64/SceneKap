$ ->
  $.get "/persons", (users) ->
    $.each users, (index, user) ->
      name = $("<div>").addClass("Name").text user.name
      age = $("<div>").addClass("Age").text user.age
      gender = $("<div>").addClass("Gender").text user.gender
      $("#persons").append $("<li>").append(name).append(age)