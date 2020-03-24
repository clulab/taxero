/* Formatting function for row details - modify as you need */
function format ( d ) {
// `d` is the original data object for the row
    var t = '<table cellpadding="1" cellspacing="0" border="0" style="padding-left:50px;">'
    t = t + '<tr>' +
         '<td> DocID </td>' +
         '<td> Sentence Text </td>' +
        '</tr>';
    for (let i = 0; i < d.evidence.length; i++) {
        t = t + '<tr>' +
                 '<td>' + d.evidence[i].id + '</td>' +
                 '<td>' + d.evidence[i].sentence + '</td>' +
                '</tr>';
    }
    return t + '</table>';
    //return '<table cellpadding="5" cellspacing="0" border="0" style="padding-left:50px;">'+
    //    '<tr>'+
    //        '<td>Sentence:</td>'+
    //        '<td>'+d.evidence+'</td>'+
    //    '</tr>'+
    //'</table>';
}

$(document).ready(function () {

    $('form').submit(function (event) {

        // stop the form from submitting the normal way and refreshing the page
        event.preventDefault();

        // collect form data
        var query = $('#query').val();
        var rules = $('#rules').val();
        var formData = {
            'query': query,
            'rules': rules
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
            url: 'customRules',
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
            var table = $('#results').DataTable({
                data: data,
                columns: [
                    {
                        "className":      'details-control',
                        "orderable":      false,
                        "data":           null,
                        "defaultContent": ''
                    },
                    { data: "query" },
                    { data: "result" },
                    { data: "count" },
                    { data: "similarity" },
                    { data: "score" }
                ]
            });



            // Add event listener for opening and closing details
            $('#results tbody').on('click', 'td.details-control', function () {
                var tr = $(this).closest('tr');
                var row = table.row( tr );

                if ( row.child.isShown() ) {
                    // This row is already open - close it
                    row.child.hide();
                    tr.removeClass('shown');
                }
                else {
                    // Open this row
                    row.child( format(row.data()) ).show();
                    tr.addClass('shown');
                }
            } );
            // hide spinner
            document.getElementById("overlay").style.display = "none";
        });

    });

});
