$(document).ready(function () {

    $('form').submit(function (event) {

        // stop the form from submitting the normal way and refreshing the page
        event.preventDefault();

        // collect form data
        var rule = $('#rule').val();
        var formData = {
            'rule': rule
        }

        if (!formData.query.trim()) {
            alert("Please write something.");
            return;
        }

        if ($.fn.DataTable.isDataTable('#results')) {
            $('#results').DataTable().clear().destroy();
        }

        // show spinner
        document.getElementById("overlay").style.display = "block";

        // process the form
        $.ajax({
            type: 'GET',
            url: 'customRule',
            data: formData,
            dataType: 'json',
            encode: true
        })
        .fail(function (jqXHR, textStatus) {
            // hide spinner
            document.getElementById("overlay").style.display = "none";
            console.log(jqXHR);
            console.log(textStatus);
            alert("request failed: " + textStatus);
        })
        .done(function (data) {
            console.log(data);
            $('#results').DataTable({
                data: data,
                columns: [
                    { title: "query" },
                    { title: "result" },
                    { title: "count" },
                    { title: "score" }
                ]
            });
            // hide spinner
            document.getElementById("overlay").style.display = "none";
        });

    });

});
